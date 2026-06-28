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
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import space.kscience.controls.api.*
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.dataplatform.storage.storeData
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.opcua.client.readMetaWithTime
import space.kscience.controls.opcua.server.fromOpc
import space.kscience.controls.plc4x.Plc4xProperty
import space.kscience.controls.plc4x.throwOnFail
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toKotlinInstant

/**
 * Represents a table of tags that provides a mechanism to interact with
 * dynamically changing values in a data platform. This interface enables
 * reading, monitoring, and managing these values as time series data or
 * stateful properties.
 *
 * It extends the following:
 * - `ContextAware`: Ensures access to a `Context` for configuration and operation.
 * - `WithLifeCycle`: Provides lifecycle management such as starting and stopping the table.
 * - `DeviceMessageSource`: Allows access to device messages via a shared flow.
 * - `ValueStateFactory`: Enables the creation and management of observable value states.
 */
public interface TagTable : ContextAware, WithLifeCycle, DeviceMessageSource, ValueStateFactory, CoroutineScope {
    /**
     * Read a value of a single column in the table
     */
    public suspend fun read(tag: String): Meta

    /**
     * Read current values of all tags
     */
    public fun readAll(): Map<String, Meta>

    /**
     * Starts generating a flow of rows for the current data platform with a specified interval.
     *
     * @param interval the interval between row generation.
     */
    public fun readTimeSeries(interval: Duration): TimeSeriesRows<Meta>

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    public fun valueState(tag: String): ValueState<Meta>

    /**
     * List all available tags and their descriptors
     */
    public val tags: Map<String, MetaDescriptor>

    public val clock: Clock


    public object ValueFactorySpec : MetaSpec() {
        public val tag: MetaRef<String> by string()
    }

    public companion object {

        internal val timeColumnHeader: ColumnHeader<Meta> = ColumnHeader<Meta>("@time") {
            title = "Time"
        }

        public const val TAG_TABLE_FACTORY_TYPE: String = "tagTable"
    }
}

/**
 * The [PlcTagTable] is responsible for managing connections to various data source clients including OPC UA, PLC, and Modbus.
 * It provides methods to resolve clients for each source type based on their configurations.
 * The class also supports time zone and clock customization and implements the `AutoCloseable` interface for resource management.
 *
 * @param configuration The configuration object that contains the sources, timers, and properties for the platform.
 * @param timeZone The time zone setting for the platform, defaulting to the system's current time zone.
 * @param clock The clock instance used for time-related operations, defaulting to the system clock.
 */
public class PlcTagTable(
    override val context: Context,
    public val configuration: PlcTableConfiguration,
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    override val clock: Clock = context.clock,
) : TagTable, CoroutineScope {


    override val coroutineContext: CoroutineContext =
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job])

    private val opcClients = mutableMapOf<String, OpcUaClient>()

    //FIXME process connection errors

    private fun resolveOpcClient(source: String): OpcUaClient = opcClients.getOrPut(source) {
        val config = configuration.sources[source] as? OpcUaConfig ?: error("No OPC source found for $source")
        OpcUaClient.create(config.host).apply {
            connect()
        }
    }

    private val plcClients = mutableMapOf<String, PlcConnection>()

    private fun resolvePlcClient(source: String): PlcConnection = plcClients.getOrPut(source) {
        val config = configuration.sources[source] as? PlcConfig ?: error("No PLC source found for $source")
        DefaultPlcDriverManager().getConnection(config.address).apply {
            connect()
        }
    }


    private val modbusClients = mutableMapOf<String, AbstractModbusMaster>()

    private fun resolveModbusClient(source: String): AbstractModbusMaster = modbusClients.getOrPut(source) {
        val config = configuration.sources[source] as? ModbusConfig ?: error("No Modbus source found for $source")
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


    internal suspend fun read(propertyConfig: TagTableColumn): ValueWithTime<Meta> = when (propertyConfig) {
        is ModbusTagTableColumn -> with(propertyConfig) {
            val client = resolveModbusClient(source)

            val meta = reader.read(client, unitId, address)

            ValueWithTime(meta, clock.now())
        }

        is OpcTagTableColumn -> with(propertyConfig) {
            val client = resolveOpcClient(source)
            client.readMetaWithTime(NodeId.parse(nodeId))
        }

        is PlcTagTableColumn -> with(propertyConfig) {
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

    /**
     * Read multiple opc properties from the same source
     */
    private suspend fun readMultipleOpc(
        source: String,
        properties: List<Map.Entry<String, OpcTagTableColumn>>,
        maxAge: Double = 500.0,
    ): List<Pair<String, ValueWithTime<Meta>>> {
        check(properties.all { it.value.source == source }) { "All properties must have the same source" }
        val client = resolveOpcClient(source)

        val dataValues = client.readValuesAsync(
            maxAge,
            TimestampsToReturn.Server,
            properties.map { NodeId.parse(it.value.nodeId) }
        ).await()

        return properties.zip(dataValues).map { (entry, response) ->
            val time = response.serverTime ?: error("No server time provided")
            val meta: Meta = Meta.fromOpc(response.value.value)
            entry.key to ValueWithTime(meta, time.javaInstant.toKotlinInstant())
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

    override suspend fun read(tag: String): Meta {
        if (tag !in propertyNames) error("Property $tag not found")
        return values[tag] ?: Meta.EMPTY
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

    private var storageJob: Job? = null

    /**
     * Read all properties on trigger
     */
    private suspend fun readAllProperties(properties: List<Map.Entry<String, TagTableColumn>>): Unit =
        coroutineScope {
            properties.groupBy { it.value.source }.forEach { (source, entries) ->
                //launch reading process for each separate source
                launch {
                    //TODO maybe partition properties beforehand to avoid unnecessary computations
                    val timeout = entries.maxOf { it.value.timeout }

                    if (entries.all { it.value is OpcTagTableColumn }) {
                        //optimization to read multiple OPC properties at once
                        withTimeout(timeout) {
                            @Suppress("UNCHECKED_CAST")
                            readMultipleOpc(
                                source,
                                entries as List<Map.Entry<String, OpcTagTableColumn>>
                            ).forEach { (propertyName, value) ->
                                values[propertyName] = value.value
                                _messageFlow.emit(
                                    PropertyChangedMessage(
                                        time = value.time,
                                        property = propertyName,
                                        value = value.value,
                                    )
                                )
                            }
                        }
                    } else {
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
        }


    override suspend fun start() {
        if (readJob != null) return
        setLifecycleState(LifecycleState.STARTED)

        val clockManager = context.request(ClockManager)

        //start read job
        readJob = launch {
            configuration.properties.entries.groupBy { it.value.timer }
                .forEach { (timerName, properties: List<Map.Entry<String, TagTableColumn>>) ->
                    val timer = configuration.timers[timerName]?.createTimerState(clockManager)
                        ?: error("Timer $timerName not found")
                    timer.subscribe().onEach {
                        readAllProperties(properties)
                    }.launchIn(this)
                }
        }

        //start storage job
        configuration.storage?.let { storageConfig ->

            //merge global parameters and per-column configuration
            val columnCompression = configuration.properties.entries.mapNotNull { (key, value) ->
                value.compression?.let { compression -> key to compression }
            }.toMap()

            val compression = if (
                storageConfig.compression == null && columnCompression.isEmpty()
            ) {
                null
            } else {
                RowsCompression(
                    skipUnchangedRows = storageConfig.compression?.skipUnchangedRows ?: true,
                    skipUnchangedValues = storageConfig.compression?.skipUnchangedValues ?: false,
                    numericDelta = storageConfig.compression?.numericDelta,
                    columns = storageConfig.compression?.columns?.plus(columnCompression) ?: columnCompression,
                )
            }

            storageJob = storeData(
                directory = Path(storageConfig.path),
                readInterval = storageConfig.readInterval,
                maxRowsPerEnvelope = storageConfig.maxRowsPerEnvelope,
                maxDuration = storageConfig.maxDuration,
                maxPause = storageConfig.maxPause,
                compression = compression,
                strategy = storageConfig.splitStrategy,
            )
        }
    }

    override suspend fun stop() {
        setLifecycleState(LifecycleState.STOPPED)
        readJob?.cancel()
        readJob = null
        storageJob?.cancel()
        storageJob = null
        opcClients.values.forEach { it.disconnect() }
        plcClients.values.forEach { it.close() }
        modbusClients.values.forEach { it.disconnect() }
    }

    override val tags: Map<String, MetaDescriptor> by lazy {
        configuration.properties.mapValues { MetaDescriptor() }
    }

    private val propertyColumnHeaders: List<ColumnHeader<Meta>> = configuration.properties.map { (name, property) ->
        SimpleColumnHeader(name, typeOf<Meta>(), property.meta)
    }

    private val tableHeaders: TableHeader<Meta> = buildList {
        add(TagTable.timeColumnHeader)
        addAll(propertyColumnHeaders)
    }

    /**
     * Read current values of all properties
     */
    override fun readAll(): Map<String, Meta> = values


    public override fun readTimeSeries(
        interval: Duration,
    ): TimeSeriesRows<Meta> = object : TimeSeriesRows<Meta> {
        override val headers: TableHeader<Meta> get() = tableHeaders

        private val rowFlow: SharedFlow<TimeSeriesValues<Meta>> = flow {
            while (true) {
                //FIXME process read errors
                val values = propertyColumnHeaders.associate { it.name to read(it.name) }
                emit(ValueWithTime(values, clock.now()))
                delay(interval)
            }
        }.shareIn(this@PlcTagTable, SharingStarted.WhileSubscribed())

        override fun subscribe() = rowFlow
    }

    private val stateCache = mutableMapOf<String, ValueState<Meta>>()

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    override fun valueState(tag: String): ValueState<Meta> = stateCache.getOrPut(tag) {
        object : ValueState<Meta> {
            override val valueWithTime: ValueWithTime<Meta>
                get() = ValueWithTime(readAll().get(tag) ?: Meta.EMPTY, clock.now())

            override fun subscribeWithTime(): Flow<ValueWithTime<Meta>> =
                messageFlow.filterIsInstance<PropertyChangedMessage>().filter { it.property == tag }.map {
                    ValueWithTime(readAll()[tag] ?: Meta.EMPTY, it.time)
                }

            override fun toString(): String = "ValueState.dataPlatform(propertyName=$tag)"
        }
    }


    override fun build(context: Context, meta: Meta): ValueState<Meta> {
        val tag = meta[TagTable.ValueFactorySpec.tag] ?: error("No tag specified")
        return valueState(tag)
    }

    override val descriptor: MetaDescriptor get() = TagTable.ValueFactorySpec.descriptor
}

///**
// * Builds a device group using the provided constructor device scheme.
// *
// * @param scheme The construction scheme that defines the configuration and structure of the device group.
// * @return A new instance of DeviceGroup created based on the provided scheme and associated state factories.
// */
//public fun TagDataTable.buildDeviceGroup(
//    scheme: DeviceConfiguration
//): DeviceConstructor {
//    val valueStateFactories = ValueState.defaultValueStateFactories + (TagDataTable.PLATFORM_VALUE_FACTORY_TYPE to this)
//    return context.request(Construc)buildDeviceGroupByScheme(scheme, valueStateFactories)
//}


/**
 * Register a device property that is bound to a [TagTable] source.
 */
public fun DeviceConstructor.dataPlatformProperty(
    platform: TagTable,
    propertyName: String,
    dataPlatformTag: String = propertyName,
    description: String? = null,
): ValueState<Meta> = registerProperty(
    converter = MetaConverter.meta,
    descriptor = PropertyDescriptor(propertyName, description),
    state = platform.valueState(dataPlatformTag)
)