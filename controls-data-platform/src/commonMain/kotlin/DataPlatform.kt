package space.kscience.controls.dataplatform

import com.ghgande.j2mod.modbus.facade.AbstractModbusMaster
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.util.SerialParameters
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import org.apache.plc4x.java.DefaultPlcDriverManager
import org.apache.plc4x.java.api.PlcConnection
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import space.kscience.controls.api.*
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.opcua.client.readMetaWithTime
import space.kscience.controls.plc4x.Plc4xProperty
import space.kscience.controls.plc4x.throwOnFail
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * The [DataPlatform] is responsible for managing connections to various data source clients including OPC UA, PLC, and Modbus.
 * It provides methods to resolve clients for each source type based on their configurations.
 * The class also supports time zone and clock customization and implements the `AutoCloseable` interface for resource management.
 *
 * @param configuration The configuration object that contains the sources, timers, and properties for the platform.
 * @param timeZone The time zone setting for the platform, defaulting to the system's current time zone.
 * @param clock The clock instance used for time-related operations, defaulting to the system clock.
 */
public class DataPlatform(
    override val context: Context,
    public val configuration: DataPlatformConfiguration,
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    public val clock: Clock = context.clock,
) : ContextAware, WithLifeCycle, DeviceMessageSource, CoroutineScope {


    override val coroutineContext: CoroutineContext =
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])

    private val opcClients = mutableMapOf<Name, OpcUaClient>()

    //FIXME process connection errors

    public suspend fun resolveOpcClient(name: Name): OpcUaClient = opcClients.getOrPut(name) {
        val config = configuration.sources[name] as? OpcUaConfig ?: error("No OPC source found for $name")
        OpcUaClient.create(config.host).apply {
            connect()
        }
    }

    private val plcClients = mutableMapOf<Name, PlcConnection>()

    public suspend fun resolvePlcClient(name: Name): PlcConnection = plcClients.getOrPut(name) {
        val config = configuration.sources[name] as? PlcConfig ?: error("No PLC source found for $name")
        DefaultPlcDriverManager().getConnection(config.address).apply {
            connect()
        }
    }


    private val modbusClients = mutableMapOf<Name, AbstractModbusMaster>()
    public suspend fun resolveModbusClient(name: Name): AbstractModbusMaster = modbusClients.getOrPut(name) {
        val config = configuration.sources[name] as? ModbusConfig ?: error("No Modbus source found for $name")
        when (config) {
            is ModbusRtuConfig -> {
                val serialParameters = SerialParameters().apply {
                    this.portName = config.portName
                    this.baudRate = config.baudRate
                    this.databits = config.databits
                    this.stopbits = config.stopbits
                    this.parity = config.parity
                    this.flowControlIn = config.flowControlIn
                    this.flowControlOut = config.flowControlOut
                }
                ModbusSerialMaster(serialParameters, config.timeout, config.transmitDelay)
            }

            is ModbusTcpConfig -> ModbusTCPMaster(config.addr, config.port, config.timeout, true)
        }.apply {
            connect()
        }
    }


    public suspend fun read(propertyConfig: PlatformProperty): ValueWithTime<Meta> = when (propertyConfig) {
        is ModbusPlatformProperty -> with(propertyConfig) {
            val client = resolveModbusClient(source)

            val meta = reader.read(client, unitId, address)

            ValueWithTime(meta, clock.now())
        }

        is OpcPlatformProperty -> with(propertyConfig) {
            val client = resolveOpcClient(source)
            client.readMetaWithTime(NodeId.parse(nodeId))
        }

        is PlcPlatformProperty -> with(propertyConfig) {
            val connection = resolvePlcClient(source)

            require(connection.metadata.isReadSupported) { "Read actions are not supported on connections" }

            with(Plc4xProperty(address, plcValueType, name)) {
                val request = connection.readRequestBuilder().request().build()
                val response = request.execute().await()
                response.throwOnFail()

                val time = response.getDateTime(name).toKotlinLocalDateTime().toInstant(timeZone)
                val value = response.readProperty()
                return ValueWithTime(value, time)
            }
        }
    }

    //TODO provide a way to read multiple properties at once.

    private val values = mutableMapOf<String, Meta>()

    private val _messageFlow = MutableSharedFlow<DeviceMessage>(
        extraBufferCapacity = configuration.properties.size * 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val messageFlow: SharedFlow<DeviceMessage> get() = _messageFlow


    private val propertyNames = configuration.properties.keys

    public suspend fun readProperty(propertyName: String): Meta {
        if (propertyName !in propertyNames) error("Property $propertyName not found")
        return values[propertyName] ?: Meta.EMPTY
    }

    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    private suspend fun setLifecycleState(lifecycleState: LifecycleState) {
        this.lifecycleState = lifecycleState
        _messageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }

    private var readJob: Job? = null

    override suspend fun start() {
        if (readJob != null) return
        setLifecycleState(LifecycleState.STARTED)

        val clockManager = context.request(ClockManager)

        readJob = launch {
            configuration.properties.entries.groupBy { it.value.timer }.forEach { (timerName, properties) ->
                val timer = configuration.timers[timerName]?.createTimerState(clockManager)
                    ?: error("Timer $timerName not found")
                timer.subscribe().onEach {
                    coroutineScope {
                        properties.groupBy { it.value.source }.forEach { (source, entries) ->
                            //launch reading process for each separate source
                            launch {
                                //TODO implement reading multiple properties at once here. They are already grouped by source
                                entries.forEach { (propertyName, property) ->
                                    try {
                                        withTimeout(property.timeout) {
                                            val value = read(property)

                                            values[propertyName] = value.value
                                            _messageFlow.emit(
                                                PropertyChangedMessage(
                                                    time = value.time,
                                                    property = propertyName,
                                                    value = value.value,
                                                )
                                            )
                                        }
                                    } catch (ex: Exception) {
                                        logger.error(ex) { "Failed to read property $propertyName" }
                                    }
                                }
                            }
                        }
                    }
                }.launchIn(this)
            }
        }
    }

    override suspend fun stop() {
        setLifecycleState(LifecycleState.STOPPED)
        readJob?.cancel()
        readJob = null
        opcClients.values.forEach { it.disconnect() }
        plcClients.values.forEach { it.close() }
        modbusClients.values.forEach { it.disconnect() }
    }

    private val propertyColumnHeaders: List<ColumnHeader<Meta>> = configuration.properties.map { (name, property) ->
        SimpleColumnHeader(name, typeOf<Meta>(), property.meta)
    }

    private val tableHeaders: TableHeader<Meta> = buildList {
        add(timeColumnHeader)
        addAll(propertyColumnHeaders)
    }

    /**
     * Read current values of all properties
     */
    public fun readValues(): Map<String, Meta> = values

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
        }.shareIn(this@DataPlatform, SharingStarted.WhileSubscribed())

        override fun subscribe() = rowFlow

    }

    public companion object {
        internal val timeColumnHeader: ColumnHeader<Meta> = ColumnHeader<Meta>("@time") {
            title = "Time"
        }
    }
}

/**
 * Create a [ValueState] for a property of a [DataPlatform].
 */
public fun DataPlatform.valueState(tag: String): ValueState<Meta> = object : ValueState<Meta> {
    override val valueWithTime: ValueWithTime<Meta>
        get() = ValueWithTime(readValues().get(tag) ?: Meta.EMPTY, clock.now())

    override fun subscribeWithTime(): Flow<ValueWithTime<Meta>> = messageFlow.filterIsInstance<PropertyChangedMessage>()
        .filter { it.property == tag }.map {
            ValueWithTime(readValues().get(tag) ?: Meta.EMPTY, it.time)
        }

    override fun toString(): String = "ValueState.dataPlatform(propertyName=$tag)"
}

/**
 * Register a device property that is bound to a [DataPlatform] source.
 */
public fun DeviceConstructor.dataPlatformProperty(
    platform: DataPlatform,
    propertyName: String,
    dataPlatformTag: String = propertyName,
    description: String? = null,
): ValueState<Meta> = registerProperty(
    converter = MetaConverter.meta,
    descriptor = PropertyDescriptor(propertyName, description),
    state = platform.valueState(dataPlatformTag)
)