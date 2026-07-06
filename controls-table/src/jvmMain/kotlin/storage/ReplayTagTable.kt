package space.kscience.controls.tagtable.storage

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.instant
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Replay table values from storage represented by [storageIndex]
 *
 * Only tags in [tags] are shown. If given tags are not present in currently replayed storage, they are returned empty.
 */
public class ReplayTagTable(
    private val storageIndex: DataStorageIndex,
    override val tags: Map<String, MetaDescriptor>,
    override val clock: Clock = storageIndex.context.clock
) : TagTable, Replay {

    override val context: Context get() = storageIndex.context

    private val _messageFlow = MutableSharedFlow<DeviceMessage>()

    override val messageFlow: Flow<DeviceMessage> get() = _messageFlow

    override val coroutineContext: CoroutineContext get() = context.coroutineContext

    private var values: MutableMap<String, Meta> = ConcurrentHashMap()

    /**
     * Read a value of a single column in the table
     */
    override suspend fun read(tag: String): Meta = values[tag] ?: Meta.EMPTY

    /**
     * Read current values of all tags
     */
    override fun readAll(): Map<String, Meta> = values

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     */
    override fun readTimeSeries(interval: Duration): TimeSeriesRows<Meta> {

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
                val values = propertyColumnHeaders.associate { it.name to read(it.name) }
                emit(ValueWithTime(values, clock.now()))
                delay(interval)
            }
        }.shareIn(this, SharingStarted.WhileSubscribed())

        return TimeSeriesRowsFlow(tableHeaders, rowFlow)
    }

    override suspend fun play(
        from: Instant,
        to: Instant,
        startTime: Instant?,
        timeScale: Double
    ) {
        require(timeScale > 0.0) { "timeScale must be greater than 0.0" }
        require(from >= to) { "from must be less than or equal to to" }

        suspend fun processRow(row: Row<Meta>, time: Instant) {
            tags.keys.forEach { tag ->
                val value = row.getOrNull(tag)
                if (value != null) {
                    values[tag] = value
                    _messageFlow.emit(
                        PropertyChangedMessage(
                            time = time,
                            property = tag,
                            value = value,
                        )
                    )
                }
            }
            _messageFlow.emit(
                PropertyChangedMessage(
                    time = time,
                    property = TagTable.ROW_PROPERTY_NAME,
                    value = Meta {
                        values.forEach { (key, value) ->
                            set(key, value)
                        }
                    },
                )
            )
        }

        val rows = storageIndex.selectRows(from..to)
        var time: Instant = startTime ?: clock.now()

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

    override fun valueState(tag: String): ValueState<Meta> = stateCache.getOrPut(tag) {
        TagTableValueState(this, tag)
    }

    override suspend fun start() {
        setLifecycleState(LifecycleState.STARTING)
        storageIndex.open()
        setLifecycleState(LifecycleState.STOPPED)
    }

    override suspend fun stop() {
        storageIndex.close()
        setLifecycleState(LifecycleState.STOPPED)
    }


}