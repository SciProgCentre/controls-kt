package space.kscience.controls.tagtable

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
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.opcua.client.readMetaWithTime
import space.kscience.controls.opcua.server.fromOpc
import space.kscience.controls.plc4x.Plc4xProperty
import space.kscience.controls.plc4x.throwOnFail
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.SingleFileEnvelopeOperations
import space.kscience.controls.tagtable.TagState.Companion.quality
import space.kscience.controls.tagtable.storage.storeData
import space.kscience.controls.tagtable.timeseries.TimeSeriesRows
import space.kscience.controls.tagtable.timeseries.TimeSeriesRowsFlow
import space.kscience.controls.tagtable.timeseries.TimeSeriesValues
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.set
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

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
    public val configuration: TagTableConfiguration,
    public val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    override val clock: Clock = context.clock,
) : TagTable, CoroutineScope {


    override val coroutineContext: CoroutineContext =
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job.Key])

    private val opcClients = mutableMapOf<String, OpcUaClient>()


    private val tagStates = ConcurrentHashMap<String, TagState>()

    private fun updateTagState(tag: String, tagQuality: String, metaBuilder: MutableMeta.()->Unit = {}){
        tagStates[tag] = TagState(Meta {
            set(quality, tagQuality)
            metaBuilder()
        })
    }

    private fun resolveOpcClient(source: String): OpcUaClient = opcClients.getOrPut(source) {
        val config = configuration.sources[source] as? OpcUaConfig ?: error("No OPC source found for $source")
        //TODO add certificate configuration
        OpcUaClient.create(config.host).apply {
            connect()
        }
    }

    private val plcClients = mutableMapOf<String, PlcConnection>()

    private fun resolvePlcClient(source: String): PlcConnection = plcClients.getOrPut(source) {
        val config = configuration.sources[source] as? Plc4xConfig ?: error("No PLC source found for $source")
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


    internal suspend fun read(tagConfig: TagTableColumn): ValueWithTime<Meta> = when (tagConfig) {
        is ModbusTagTableColumn -> with(tagConfig) {
            val client = resolveModbusClient(source)

            val meta = reader.read(client, unitId, address)

            ValueWithTime(meta, clock.now())
        }

        is OpcTagTableColumn -> with(tagConfig) {
            val client = resolveOpcClient(source)
            client.readMetaWithTime(NodeId.parse(nodeId))
        }

        is PlcTagTableColumn -> with(tagConfig) {
            val connection = resolvePlcClient(source)

            require(connection.metadata.isReadSupported) { "Read actions are not supported on connections" }

            with(Plc4xProperty(address, plcValueType, name)) {
                val request = connection.readRequestBuilder().request().build()
                val response = request.execute().await()
                response.throwOnFail()

                val time = response.getDateTime(name).toKotlinLocalDateTime().toInstant(timeZone)
                val value = response.readProperty()
                ValueWithTime(value, time)
            }
        }

        is InternalTagTableColumn -> {
            val deviceManager = context.plugins[DeviceManager] ?: error("Device manager is not found in the context")
            val value = deviceManager.readProperty(tagConfig.deviceName, tagConfig.propertyName)
            ValueWithTime(value, clock.now())
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

    private val values = ConcurrentHashMap<String, ValueWithTime<Meta>>()

    override val messageFlow: SharedFlow<DeviceMessage>
        field = MutableSharedFlow<DeviceMessage>(
            extraBufferCapacity = configuration.properties.size * 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )


    private val propertyNames = configuration.properties.keys

    override suspend fun readTag(tag: String): Meta = readTagWithTime(tag).value

    override fun readTagWithTime(tag: String): ValueWithTime<Meta> {
        if (tag !in propertyNames) error("Tag $tag not found")
        return values[tag] ?: ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST)
    }

    override suspend fun readTagState(tag: String): TagState {
        if (tag !in propertyNames) error("Tag $tag not found")
        return tagStates[tag] ?: TagState.EMPTY
    }

    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    private suspend fun setLifecycleState(lifecycleState: LifecycleState) {
        this.lifecycleState = lifecycleState
        messageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }

    private var readJob: Job? = null

    private var storageJob: Job? = null

    /**
     * Read all properties on trigger
     */
    private suspend fun readAllProperties(properties: List<Map.Entry<String, TagTableColumn>>): Unit = supervisorScope {
        properties.groupBy { it.value.source }.forEach { (source, entries) ->
            //launch reading process for each separate source
            launch {
                //TODO maybe partition properties beforehand to avoid unnecessary computations
                val timeout = entries.maxOf { it.value.timeout }

                var lastTime = Instant.DISTANT_PAST

                if (entries.all { it.value is OpcTagTableColumn }) {
                    //optimization to read multiple OPC properties at once
                    try {
                        withTimeout(timeout) {
                            @Suppress("UNCHECKED_CAST")
                            readMultipleOpc(
                                source,
                                entries as List<Map.Entry<String, OpcTagTableColumn>>
                            ).forEach { (propertyName, value) ->
                                values[propertyName] = value
                                lastTime = if (value.time > lastTime) value.time else lastTime
                                messageFlow.emit(
                                    PropertyChangedMessage(
                                        time = value.time,
                                        property = propertyName,
                                        value = value.value,
                                    )
                                )
                            }
                        }
                    }catch (ex: Exception) {
                        entries.forEach { (propertyName, _) ->
                            updateTagState(propertyName, TagState.READ_FAILED_QUALITY) {
                                ex.message?.let { set("error", it) }
                            }
                        }
                        logger.error(ex) { "Failed to read multiple properties ${entries.map { it.key }}" }
                    }
                } else {
                    entries.forEach { (propertyName, property) ->
                        try {
                            withTimeout(property.timeout) {
                                val value = read(property)
                                values[propertyName] = value
                                lastTime = if (value.time > lastTime) value.time else lastTime
                                updateTagState(propertyName, TagState.GOOD_QUALITY)
                                messageFlow.emit(
                                    PropertyChangedMessage(
                                        time = value.time,
                                        property = propertyName,
                                        value = value.value,
                                    )
                                )
                            }
                        } catch (ex: Exception) {
                            updateTagState(propertyName, TagState.READ_FAILED_QUALITY){
                                ex.message?.let { set("error", it) }
                            }
                            logger.error(ex) { "Failed to read property $propertyName" }
                        }
                    }
                }
                //emit row change
                if (lastTime > Instant.DISTANT_PAST) {
                    messageFlow.emit(
                        PropertyChangedMessage(
                            time = lastTime,
                            property = TagTable.ROW_PROPERTY_NAME,
                            value = Meta {
                                values.forEach { (key, value) ->
                                    set(key, value.value)
                                }
                            },
                        )
                    )
                }
            }
        }
    }


    override suspend fun start() {
        if (readJob != null) return
        setLifecycleState(LifecycleState.STARTED)

        val clockManager = context.request(ClockManager.Companion)

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

            val storagePlugin = context.request(ControlsStoragePlugin)

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
                withTagState = storageConfig.withTagState,
                maxRowsPerEnvelope = storageConfig.maxRowsPerEnvelope,
                maxDuration = storageConfig.maxDuration,
                maxPause = storageConfig.maxPause,
                compression = compression,
                strategy = storageConfig.splitStrategy,
                rowsConverter = storagePlugin.rowEnvelopeConverters[storageConfig.rowsConverterType]
                    ?: error("No row envelope converter found for type ${storageConfig.rowsConverterType}"),
                operations = if (storageConfig.separateMeta) {
                    NativeFileEnvelopeOperations(context.io)
                } else {
                    SingleFileEnvelopeOperations(context.io)
                }
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
    override fun readAllValues(): Map<String, Meta> = values.mapValues { it.value.value }


    public override fun readTimeSeries(
        interval: Duration,
        withTagState: Boolean
    ): TimeSeriesRows<Meta> {
        val rowFlow: SharedFlow<TimeSeriesValues<Meta>> = flow {
            while (true) {
                val values = if (withTagState) {
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

    private val stateCache = mutableMapOf<String, ValueState<Meta>>()

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    override fun subscribe(tag: String): ValueState<Meta> = stateCache.getOrPut(tag) {
        TagTableValueState(this, tag)
    }
}
