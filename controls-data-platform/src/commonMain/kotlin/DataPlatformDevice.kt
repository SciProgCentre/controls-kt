package space.kscience.controls.dataplatform

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.*
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * A device that exposes property values in a data platform
 */
public class DataPlatformDevice(
    override val context: Context,
    public val configuration: DataPlatformConfiguration
) : Device {

    override val coroutineContext: CoroutineContext =
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])

    private val clockManager = context.request(ClockManager)

    override val clock: Clock = clockManager.clock

    public val platform: DataPlatform = DataPlatform(context, configuration)

    private val values = mutableMapOf<Name, Meta>()

    private val _messageFlow = MutableSharedFlow<DeviceMessage>(configuration.properties.size * 2)

    override val messageFlow: SharedFlow<DeviceMessage> get() = _messageFlow

    private val readJob = launch {
        configuration.properties.entries.groupBy { it.value.timer }.forEach { (timerName, properties) ->
            val timer = configuration.timers[timerName]?.timer(clockManager) ?: error("Timer $timerName not found")
            timer.subscribe().onEach { instant ->
                properties.forEach { (propertyName, property) ->
                    val value = property.read(platform)
                    values[propertyName] = value.value
                    _messageFlow.emit(
                        PropertyChangedMessage(
                            time = value.time,
                            property = propertyName.toString(),
                            value = value.value,
                        )
                    )
                }
            }.launchIn(this)
        }
    }

    override val propertyDescriptors: Collection<PropertyDescriptor> =
        configuration.properties.map { (name, platformProperty) ->
            PropertyDescriptor(name.toString())
            //TODO add type descriptors
        }

    override val actionDescriptors: Collection<ActionDescriptor> = emptyList()

    private val propertyNames = configuration.properties.keys.map { it.toString() }

    override suspend fun readProperty(propertyName: String): Meta {
        if (propertyName !in propertyNames) error("Property $propertyName not found")
        return values[propertyName.parseAsName(true)] ?: Meta.EMPTY
    }

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        error("Write is not supported")
    }

    override suspend fun execute(
        actionName: String,
        argument: Meta?
    ): Meta? = null

    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    private suspend fun setLifecycleState(lifecycleState: LifecycleState) {
        this.lifecycleState = lifecycleState
        _messageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }

    final override suspend fun start() {
        if (lifecycleState == LifecycleState.STOPPED) {
            super.start()
            setLifecycleState(LifecycleState.STARTING)
            setLifecycleState(LifecycleState.STARTED)
        } else {
            logger.debug { "Device $this is already started" }
        }
    }

    final override suspend fun stop() {
        setLifecycleState(LifecycleState.STOPPED)
        super.stop()
    }

    private val propertyColumnHeaders: List<ColumnHeader<Meta>> = configuration.properties.map { (name, property) ->
        SimpleColumnHeader(name.toString(), typeOf<Meta>(), property.meta)
    }

    private val tableHeaders: TableHeader<Meta> = buildList {
        add(timeColumnHeader)
        addAll(propertyColumnHeaders)
    }

    /**
     * Read current values of all properties
     */
    public suspend fun readValues(): Map<Name, Meta> = values

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     */
    public fun readTimeSeries(
        interval: Duration,
    ): TimeSeriesRows<Meta> = object : TimeSeriesRows<Meta> {
        override val headers: TableHeader<Meta> get() = tableHeaders

        private val rowFlow: SharedFlow<TimeSeriesValues<Meta>> = flow {
            while (true) {
                val values = propertyColumnHeaders.associate { it.name to readProperty(it.name) }
                emit(ValueWithTime(values, clock.now()))
                delay(interval)
            }
        }.shareIn(this@DataPlatformDevice, SharingStarted.WhileSubscribed())

        override fun subscribe() = rowFlow

    }

    public companion object {
        internal val timeColumnHeader: ColumnHeader<Meta> = ColumnHeader<Meta>("@time") {
            title = "Time"
        }
    }

}