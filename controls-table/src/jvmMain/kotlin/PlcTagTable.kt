package space.kscience.controls.tagtable

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import space.kscience.controls.api.*
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.VirtualValueState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.opcua.server.Opc
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileDeviceMessageStorage
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.SingleFileEnvelopeOperations
import space.kscience.controls.tagtable.storage.storeData
import space.kscience.controls.tagtable.timeseries.TimeSeriesRows
import space.kscience.controls.tagtable.timeseries.TimeSeriesRowsFlow
import space.kscience.controls.tagtable.timeseries.TimeSeriesValues
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.controls.time.timeZone
import space.kscience.dataforge.context.*
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.firstOrNull
import space.kscience.dataforge.names.startsWith
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.math.abs
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.measureTime

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
    public val timeZone: TimeZone = context.timeZone,
    override val clock: Clock = context.clock,
) : TagTable, CoroutineScope {

    override val coroutineContext: CoroutineContext =
        context.coroutineContext + Dispatchers.Default + SupervisorJob(context.coroutineContext[Job.Key])

    override val tags: Map<String, MetaDescriptor> = configuration.tags.mapValues { MetaDescriptor() }

    override val propertyDescriptors: Collection<PropertyDescriptor> = buildList {
        tags.forEach { (name, descriptor) ->
            add(PropertyDescriptor(name, metaDescriptor = descriptor))
            add(PropertyDescriptor(name + TagState.TAG_QUALITY_SUFFIX, metaDescriptor = TagState.descriptor))
        }
    }

    override val actionDescriptors: Collection<ActionDescriptor> = emptyList()

    /**
     * Contains both tag values and tag states
     */
    private val values: Map<String, VirtualValueState<Meta>> = buildMap {
        tags.forEach { (tag, descriptor) ->
            put(tag, VirtualValueState(Meta.EMPTY, clock))
            put(tag + TagState.TAG_QUALITY_SUFFIX, VirtualValueState(Meta.EMPTY, clock))
        }
    }

    override val messageFlow: SharedFlow<DeviceMessage>
        field = MutableSharedFlow<DeviceMessage>(
            extraBufferCapacity = configuration.tags.size,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private suspend fun updateTagState(tag: String, tagState: TagState) {
        val valueState = values[tag + TagState.TAG_QUALITY_SUFFIX] ?: error("Tag $tag not found")
        valueState.value = tagState.value
    }

    private suspend fun updateTagState(
        tag: String,
        tagQuality: String,
        metaBuilder: MutableMeta.() -> Unit = {}
    ) = updateTagState(
        tag = tag,
        tagState = TagState(
            Meta {
                set(TagState.quality, tagQuality)
                metaBuilder()
            }
        )
    )

    /**
     * Compute tag state and tag new value based on previous value and current raw value.
     * Filter value if necessary
     */
    private suspend fun processTag(
        tag: String,
        column: TagTableColumn,
        previousValue: ValueWithTime<Meta>?,
        rawValue: ValueWithTime<Meta>
    ): ValueWithTime<Meta> {
        val tagQualityValue = MutableMeta()

        tagQualityValue[TagState.quality] = TagState.GOOD_QUALITY

        // process opc status
        val opcStatus = Opc.getOpcStatus(rawValue.value)

        if (opcStatus?.isGood == false) {
            tagQualityValue[TagState.quality] = TagState.BAD_QUALITY
            tagQualityValue["opcStatus"] = opcStatus.value
        }

        // process maximal age
        column.maxAge?.let { maxAge ->
            val age = clock.now() - rawValue.time
            if (age > maxAge) {
                tagQualityValue[TagState.quality] = TagState.BAD_QUALITY
                tagQualityValue["age"] = age.toString()
            }
        }

        // process max delta
        column.maxDelta?.let {
            val oldValue = previousValue?.value?.double
            val newValue = rawValue.value.double
            if (oldValue != null && newValue != null && abs(newValue - oldValue) > column.maxDelta) {
                tagQualityValue[TagState.quality] = TagState.BAD_QUALITY
                tagQualityValue["delta"] = newValue - oldValue
            }
        }

        // commit tag state
        updateTagState(tag, TagState(tagQualityValue.seal()))


        val value = if (column.removeAuxiliaryValues) {
            Meta {
                rawValue.value.valueSequence().forEach { (name, value) ->
                    //filter names starting with '@'
                    if (name.firstOrNull()?.body?.first() != '@') {
                        set(name, value)
                    }
                }
            }
        } else {
            rawValue.value
        }

        return ValueWithTime(value, rawValue.time)
    }

    private val sources = mutableMapOf<String, TagSource>()

    private suspend fun getSource(sourceName: String): TagSource = sources.getOrPut(sourceName) {
        if (sourceName == "@controls") return@getOrPut ControlsTagSource(context.request(DeviceManager))
        val sourceConfig = configuration.sources[sourceName] ?: error("No source found for $sourceName")

        logger.debug { "PlcTagTable is creating source $sourceName" }

        when (sourceConfig) {
            is OpcTagSourceConfig -> OpcTagSource(sourceConfig, this, clock)
            is ModbusTagSourceConfig -> ModbusTagSource(sourceConfig, this, clock)
            is Plc4xTagSourceConfig -> Plc4xTagSource(sourceConfig, this, timeZone)
        }.also { tagSource ->
            tagSource.state.onEach {
                messageFlow.emit(
                    PropertyChangedMessage(
                        time = clock.now(),
                        sourceDevice = Name.of(SOURCE_DEVICE_KEY, sourceName),
                        property = "state",
                        value = it.value
                    )
                )
            }.launchIn(this)

            logger.info { "PlcTagTable is connecting to the source $sourceName" }

            // launches connection procedure. Does not wait for it to complete
            tagSource.start()
        }
    }

    /**
     * Read multiple opc properties from the same source
     *
     * Returns null if the source is not connected
     */
    internal suspend fun read(tagConfig: TagTableColumn): ValueWithTime<Meta>? {
        return getSource(tagConfig.source).readTag(tagConfig.config)
    }

    override suspend fun readTag(tag: String): Meta = readTagWithTime(tag).value

    override fun readTagWithTime(tag: String): ValueWithTime<Meta> {
        val state = values[tag] ?: error("Tag $tag not found")
        return state.valueWithTime
    }

    override fun getCachedProperty(propertyName: String): Meta? = values[propertyName]?.value

    override suspend fun invalidate(propertyName: String) {
        // does nothing
    }

    override fun subscribeToProperty(propertyName: String): Flow<ValueWithTime<Meta>> =
        values[propertyName]?.subscribeWithTime() ?: error("Property $propertyName not found")

    override suspend fun readTagQuality(tag: String): TagState {
        val valueState = values[tag + TagState.TAG_QUALITY_SUFFIX] ?: error("Tag $tag not found")
        return TagState(valueState.value)
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

    private var messageStorageJob: Job? = null

    /**
     * Read all properties on trigger
     */
    private suspend fun readAllProperties(
        properties: List<Map.Entry<String, TagTableColumn>>
    ): Unit = supervisorScope {
        properties.groupBy { it.value.source }.forEach { (source, entries) ->
            //launch reading process for each separate source
            launch {
                val timeout = entries.maxOf { it.value.timeout }

                var lastTime = Instant.DISTANT_PAST

                try {
                    withTimeout(timeout) {
                        val source = getSource(source)

                        val result = source.readMultipleTags(entries.map { it.key to it.value.config })
                        if (result != null) {
                            result.forEach { (tag, value) ->
                                value.getOrNull()?.let { rawValue ->
                                    val column = configuration.tags[tag]
                                        ?: error("Configuration for tag $tag not found")
                                    val valueState = values[tag] ?: error("Tag $tag not found")

                                    val newValue = processTag(tag, column, valueState.valueWithTime, rawValue)
                                    valueState.valueWithTime = newValue

                                    lastTime = if (newValue.time > lastTime) newValue.time else lastTime
                                    messageFlow.emit(
                                        PropertyChangedMessage(
                                            time = newValue.time,
                                            property = tag,
                                            value = newValue.value,
                                        )
                                    )
                                } ?: run {
                                    updateTagState(tag, TagState.READ_FAILED_QUALITY)
                                }
                            }
                        } else {
                            entries.forEach { (propertyName, _) ->
                                updateTagState(propertyName, TagState.NOT_CONNECTED_QUALITY)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    if (ex is CancellationException) {
                        ensureActive()
                    }
                    entries.forEach { (propertyName, _) ->
                        updateTagState(propertyName, TagState.READ_FAILED_QUALITY) {
                            ex.message?.let { set("error", it) }
                        }
                    }
                    messageFlow.emit(
                        DeviceErrorMessage(
                            time = clock.now(),
                            errorMessage = "Failed to read multiple OPC properties: ${entries.map { it.key }}",
                            errorType = ex::class.simpleName,
                            errorStackTrace = ex.stackTraceToString()
                        )
                    )
                    logger.error(ex) { "Failed to read multiple properties from source $source: ${entries.map { it.key }}" }
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
            configuration.tags.entries.groupBy {
                it.value.timer
            }.forEach { (timerName, properties: List<Map.Entry<String, TagTableColumn>>) ->
                val timer = configuration.timers[timerName]?.createTimerState(clockManager)
                    ?: error("Timer $timerName not found")

                timer.subscribe().buffer(0).onEach { time ->
                    measureTime {
                        readAllProperties(properties)
                    }.let {
                        logger.info { "Reading properties for timer $timerName at $time took $it" }
                    }
                }.launchIn(this)
            }
        }

        //start storage job
        configuration.storage?.let { storageConfig ->

            val storagePlugin = context.request(ControlsStoragePlugin)

            //merge global parameters and per-column configuration
            val columnCompression = configuration.tags.entries.mapNotNull { (key, value) ->
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
                includeOldValues = storageConfig.writeOldValues,
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

        //start message recording job
        configuration.messageStorage?.let { messageStorageConfiguration ->
            val storage = FileDeviceMessageStorage(
                directory = Path(messageStorageConfiguration.path),
                maxMessagesPerFile = messageStorageConfiguration.maxMessagesPerFile,
                clock = clock
            )
            messageStorageJob = messageFlow.onEach {
                if (it.sourceDevice?.startsWith(SOURCE_DEVICE_KEY) == true) {
                    storage.write(it)
                }
            }.launchIn(this)
        }
    }

    override suspend fun stop() {
        setLifecycleState(LifecycleState.STOPPED)
        readJob?.cancel()
        readJob = null
        storageJob?.cancel()
        storageJob = null
        sources.values.forEach { it.stop() }
    }

    private val propertyColumnHeaders: List<ColumnHeader<Meta>> = configuration.tags.map { (name, property) ->
        SimpleColumnHeader(name, typeOf<Meta>(), property.meta)
    }

    private val tableHeaders: TableHeader<Meta> = buildList {
        add(TagTable.timeColumnHeader)
        addAll(propertyColumnHeaders)
    }


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
    ): Meta? {
        error("Actions are not supported")
    }

    /**
     * Read current values of all properties
     */
    override fun readAllValues(): Map<String, Meta> =  values.filter { !it.key.endsWith(TagState.TAG_QUALITY_SUFFIX) }.mapValues { it.value.value }


    public override fun readTimeSeries(
        interval: Duration,
        withTagQuality: Boolean,
        includeOldValues: Boolean
    ): TimeSeriesRows<Meta> {
        val headers = if (withTagQuality) {
            tableHeaders + propertyColumnHeaders.map {
                SimpleColumnHeader<Meta>(it.name + TagState.TAG_QUALITY_SUFFIX, typeOf<Meta>(), Meta.EMPTY)
            }
        } else tableHeaders

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

                emit(ValueWithTime(values, currentTime))
                delay(interval)
            }
        }.shareIn(this, SharingStarted.WhileSubscribed())
        return TimeSeriesRowsFlow(headers, rowFlow)
    }

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    override fun subscribe(tag: String): ValueState<Meta> = values[tag] ?: error("Tag $tag not found")

    public companion object {
        /**
         * A prefix name for device messages describing sources behavior
         */
        public const val SOURCE_DEVICE_KEY: String = "source"
    }
}
