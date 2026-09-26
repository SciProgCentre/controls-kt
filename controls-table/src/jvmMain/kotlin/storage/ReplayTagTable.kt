package space.kscience.controls.tagtable.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.instant
import space.kscience.controls.storage.ControlsStoragePlugin
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
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.set
import space.kscience.tables.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
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
 */
public class ReplayTagTable(
    private val storageIndex: TableStorageIndex,
    override val tags: Map<String, MetaDescriptor>,
    private val scope: CoroutineScope = storageIndex.context,
    override val clock: Clock = storageIndex.context.clock
) : TagTable, Replay {

    override val context: Context get() = storageIndex.context

    private val _messageFlow = MutableSharedFlow<DeviceMessage>()

    override val messageFlow: SharedFlow<DeviceMessage> get() = _messageFlow

    override val coroutineContext: CoroutineContext get() = context.coroutineContext

    private val values = ConcurrentHashMap<String, ValueWithTime<Meta>>()
    private val tagStates = ConcurrentHashMap<String, TagState>()


    override fun readTagWithTime(tag: String): ValueWithTime<Meta> =
        values[tag] ?: ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST)

    /**
     * Read a value of a single column in the table
     */
    override suspend fun readTag(tag: String): Meta = readTagWithTime(tag).value

    override suspend fun readTagState(tag: String): TagState = tagStates[tag] ?: TagState.EMPTY

    /**
     * Read current values of all tags
     */
    override fun readAllValues(): Map<String, Meta> = values.mapValues { it.value.value }

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     */
    override fun readTimeSeries(interval: Duration, withTagState: Boolean): TimeSeriesRows<Meta> {

        val propertyColumnHeaders: List<ColumnHeader<Meta>> = tags.map { (name, descriptor) ->
            SimpleColumnHeader(name, typeOf<Meta>(), Meta.EMPTY)
        }

        val tableHeaders: TableHeader<Meta> = buildList {
            add(TagTable.timeColumnHeader)
            addAll(propertyColumnHeaders)
        }

        val rowFlow: SharedFlow<TimeSeriesValues<Meta>> = flow {
            while (true) {
                //FIXME process read errors
                val values =if(withTagState){
                    propertyColumnHeaders.associate { it.name to readTag(it.name) } +
                            propertyColumnHeaders.associate { (it.name + TagState.TAG_STATE_SUFFIX) to readTagState(it.name).value }
                } else {
                    propertyColumnHeaders.associate { it.name to readTag(it.name) }
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
        startTime: Instant?,
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
                    _messageFlow.emit(
                        PropertyChangedMessage(
                            time = time,
                            property = tag,
                            value = value,
                        )
                    )
                }
                val tagState = row.getOrNull(tag + TagState.TAG_STATE_SUFFIX)
                if(tagState != null) {
                    tagStates[tag] = TagState(tagState)
                }

            }
            _messageFlow.emit(
                PropertyChangedMessage(
                    time = time,
                    property = TagTable.ROW_PROPERTY_NAME,
                    value = Meta {
                        values.forEach { (key, value) ->
                            set(key, value.value)
                        }
                    },
                )
            )
        }

        val rows = storageIndex.selectRows(from..to)
        var time: Instant = startTime ?: clock.now()

        return scope.launch {
            rows.rowSequence().zipWithNext().forEachIndexed { index, (prev, next) ->
                val prevTime = prev[TagTable.timeColumnHeader].instant ?: error("Missing time column")
                val nextTime = next[TagTable.timeColumnHeader].instant ?: error("Missing time column")
                //send first element
                if (index == 0) {
                    processRow(prev, time)
                }
                val duration = (nextTime - prevTime) / timeScale
                withContext(context.deviceDispatcher) {
                    delay(duration)
                }
                time += duration
                processRow(next, time)
            }
        }.also {
            playJob = it
        }
    }


    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set

    private suspend fun setLifecycleState(state: LifecycleState) {
        this.lifecycleState = state
        _messageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
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
    cacheMetadata: Boolean = true,
    operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(storage.io),
    scope: CoroutineScope = storage.context,
    removeFilesCycleDuration: Duration = 10.minutes,
): ReplayTagTable {
    val storageIndex =
        TableStorageIndex(storage, dataDirectory, cacheMetadata, operations, scope, removeFilesCycleDuration)
    return ReplayTagTable(storageIndex, tags)
}
