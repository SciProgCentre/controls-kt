package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.dataplatform.TagTable
import space.kscience.controls.dataplatform.TagTableValueState
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRowsFlow
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
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

    private var values: Map<String, Meta> = ConcurrentHashMap()

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

    override suspend fun play(from: Instant?, useOriginalTime: Boolean, timeScale: Double) {
        TODO("Not yet implemented")
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