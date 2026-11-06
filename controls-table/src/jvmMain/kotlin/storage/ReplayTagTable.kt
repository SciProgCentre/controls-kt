package space.kscience.controls.tagtable.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.*
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.instant
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.tagtable.TagState
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.TagTableValueState
import space.kscience.controls.tagtable.timeseries.TimeSeriesRows
import space.kscience.controls.tagtable.timeseries.TimeSeriesRowsFlow
import space.kscience.controls.tagtable.timeseries.TimeSeriesValues
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.controls.time.deviceDispatcher
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.value
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.tables.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Replay table values from storage represented by [storageIndex]
 *
 * Only tags in [tags] are shown. If given tags are not present in currently replayed storage, they are returned empty.
 *
 * @param storageIndex the source storage index for tag rows replay
 * @param tags to be used in this replay
 */
public class ReplayTagTable(
    private val storageIndex: TableStorageIndex,
    override val tags: Map<String, MetaDescriptor>,
    private val scope: CoroutineScope = storageIndex.context,
    override val clock: Clock = storageIndex.context.clock
) : TagTable, ReplayMessageSource {

    override val context: Context get() = storageIndex.context

    override val messageFlow: SharedFlow<DeviceMessage>
        field = MutableSharedFlow<DeviceMessage>()

    override val coroutineContext: CoroutineContext =
        context.coroutineContext + SupervisorJob(scope.coroutineContext[Job])

    private val values = ConcurrentHashMap<String, ValueWithTime<Meta>>()
    private val tagStates = ConcurrentHashMap<String, TagState>()


    override fun readTagWithTime(tag: String): ValueWithTime<Meta> =
        values[tag] ?: ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST)

    /**
     * Read a value of a single column in the table
     */
    override suspend fun readTag(tag: String): Meta = readTagWithTime(tag).value

    override suspend fun readTagQuality(tag: String): TagState = tagStates[tag] ?: TagState.EMPTY

    /**
     * Read current values of all tags
     */
    override fun readAllValues(): Map<String, Meta> = values.mapValues { it.value.value }

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     */
    override fun readTimeSeries(
        interval: Duration,
        withTagQuality: Boolean,
        includeOldValues: Boolean
    ): TimeSeriesRows<Meta> {

        val propertyColumnHeaders: List<ColumnHeader<Meta>> = tags.map { (name, _) ->
            SimpleColumnHeader(name, typeOf<Meta>(), Meta.EMPTY)
        }

        val tableHeaders: TableHeader<Meta> = buildList {
            add(TagTable.timeColumnHeader)
            addAll(propertyColumnHeaders)
            if (withTagQuality) {
                addAll(propertyColumnHeaders.map {
                    SimpleColumnHeader<Meta>(it.name + TagState.TAG_QUALITY_SUFFIX, typeOf<Meta>(), Meta.EMPTY)
                })
            }
        }

        val rowFlow: SharedFlow<TimeSeriesValues<Meta>> = flow {
            while (true) {
                val currentTime = clock.now()
                val values = buildMap {
                    propertyColumnHeaders.forEach {
                        val valueWithTime = readTagWithTime(it.name)
                        // don't put value if !writeOldValues and value is too old
                        if (includeOldValues || (currentTime - valueWithTime.time) < interval) {
                            put(it.name, valueWithTime.value)
                            // include quality if requested
                            if (withTagQuality) {
                                put(it.name + TagState.TAG_QUALITY_SUFFIX, readTagQuality(it.name).value)
                            }
                        }
                    }
                }

                emit(ValueWithTime(values, clock.now()))
                delay(interval)
            }
        }.shareIn(this, SharingStarted.WhileSubscribed())

        return TimeSeriesRowsFlow(tableHeaders, rowFlow)
    }

    private var playJob: Job? = null

    override suspend fun play(
        from: Instant,
        to: Instant,
        startTimeOverride: Instant,
        timeScale: Double
    ): Job {
        require(timeScale > 0.0) { "timeScale must be greater than 0.0" }
        require(to >= from) { "from must be less than or equal to to" }

        check(playJob?.isActive != true) { "Can't start playback while already playing" }

        suspend fun processRow(row: Row<Meta>, time: Instant) {
            tags.keys.forEach { tag ->
                val value = row.getOrNull(tag)
                if (value != null) {
                    values[tag] = ValueWithTime(value, time)
                    messageFlow.emit(
                        PropertyChangedMessage(
                            time = time,
                            property = tag,
                            value = value,
                        )
                    )
                }
                val tagState = row.getOrNull(tag + TagState.TAG_QUALITY_SUFFIX)
                if (tagState != null) {
                    tagStates[tag] = TagState(tagState)
                }

            }
        }

        val rows = storageIndex.selectRows(from..to)


        return launch {
            var currentRowTime: Instant = startTimeOverride
            rows.rowSequence().zipWithNext().forEachIndexed { index, (prev, next) ->
                //ignore missing time (should not happen)
                val prevTime = prev[TagTable.timeColumnHeader].instant ?: run {
                    logger.error { "Missing time for row $prev" }
                    return@forEachIndexed
                }
                val nextTime = next[TagTable.timeColumnHeader].instant ?: run {
                    logger.error { "Missing time for row $next" }
                    return@forEachIndexed
                }
                //send first element
                if (index == 0) {
                    processRow(prev, currentRowTime)
                }
                val duration = (nextTime - prevTime) / timeScale

                //switch to device dispatcher to process virtual time
                withContext(context.deviceDispatcher) {
                    delay(duration)
                }
                currentRowTime += duration
                processRow(next, currentRowTime)
            }
        }.also {
            playJob = it
        }
    }

    override suspend fun pause() {
        playJob?.cancel()
    }

    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set

    private suspend fun setLifecycleState(state: LifecycleState) {
        this.lifecycleState = state
        messageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }

    override val propertyDescriptors: Collection<PropertyDescriptor> = buildList {
        tags.forEach { (name, descriptor) ->
            PropertyDescriptor(name, metaDescriptor = descriptor)
            PropertyDescriptor(name + TagState.TAG_QUALITY_SUFFIX, metaDescriptor = TagState.descriptor)
        }
    }

    override val actionDescriptors: Collection<ActionDescriptor> = setOf(
        ActionDescriptor("pause", "Pause playback"),
        ActionDescriptor(
            name = "play",
            description = "Start playback",
            inputMetaDescriptor = MetaDescriptor {
                value("from", ValueType.STRING) {

                }
                value("to", ValueType.STRING) {

                }

                value("startTime", ValueType.STRING) {
                    default(null)
                }

                value("timeScale", ValueType.NUMBER) {
                    default(1.0)
                }
            }
        )
    )

    override suspend fun readProperty(propertyName: String): Meta =
        if (propertyName.endsWith(TagState.TAG_QUALITY_SUFFIX)) {
            readTagQuality(propertyName.removeSuffix(TagState.TAG_QUALITY_SUFFIX)).value
        } else {
            readTag(propertyName)
        }

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        error("Write is not supported")
    }

    override suspend fun execute(
        actionName: String,
        argument: Meta?
    ): Meta? = when (actionName) {
        "play" -> {
            val from = argument?.get("from")?.instant ?: Instant.DISTANT_PAST
            val to = argument?.get("to")?.instant ?: Instant.DISTANT_FUTURE
            val startTime = argument?.get("startTime")?.instant ?: from
            val timeScale = argument?.get("timeScale")?.double ?: 1.0
            play(
                from = from,
                to = to,
                startTimeOverride = startTime,
                timeScale = timeScale
            )
            null
        }

        "pause" -> {
            pause()
            null
        }

        else -> error("Unknown action $actionName")
    }


    private val stateCache = mutableMapOf<String, ValueState<Meta>>()

    override fun subscribe(tag: String): ValueState<Meta> = stateCache.getOrPut(tag) {
        TagTableValueState(this, tag)
    }

    override suspend fun start() {
        setLifecycleState(LifecycleState.STARTING)
        storageIndex.start()
        setLifecycleState(LifecycleState.STARTED)
    }

    override suspend fun stop() {
        storageIndex.stop()
        setLifecycleState(LifecycleState.STOPPED)
        playJob?.cancel()
        playJob = null
    }
}

/**
 * Creates a [TagTable] that replays data from a file.
 */
public fun TagTable.Companion.replay(
    storage: ControlsStoragePlugin,
    dataDirectory: Path,
    tags: Map<String, MetaDescriptor>,
    operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(storage.io),
    scope: CoroutineScope = storage.context,
    removeFilesCycleDuration: Duration = 10.minutes,
): ReplayTagTable = ReplayTagTable(
    storageIndex = TableStorageIndex(
        storage = storage,
        dataDirectory = dataDirectory,
        operations = operations,
        scope = scope,
        removeFilesCycleDuration = removeFilesCycleDuration
    ),
    tags = tags
)
