package space.kscience.controls.tagtable

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import space.kscience.controls.api.*
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.opcua.server.Opc
import space.kscience.controls.storage.ControlsStoragePlugin
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
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.firstOrNull
import space.kscience.tables.ColumnHeader
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.math.abs
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

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
        context.coroutineContext + SupervisorJob(context.coroutineContext[Job.Key])


    private val values = ConcurrentHashMap<String, ValueWithTime<Meta>>()

    override val messageFlow: SharedFlow<DeviceMessage>
        field = MutableSharedFlow<DeviceMessage>(
            extraBufferCapacity = configuration.tags.size * 10,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val tagStates = ConcurrentHashMap<String, TagState>()

    private suspend fun updateTagState(tag: String, tagState: TagState) {
        tagStates[tag] = tagState
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

        when (sourceConfig) {
            is OpcTagSourceConfig -> OpcTagSource(sourceConfig, this, clock)
            is ModbusTagSourceConfig -> ModbusTagSource(sourceConfig, this, clock)
            is Plc4xTagSourceConfig -> Plc4xTagSource(sourceConfig, this, timeZone)
        }.also { tagSource ->
            tagSource.state.onEach {
                messageFlow.emit(
                    PropertyChangedMessage(
                        time = clock.now(),
                        property = "@source.$sourceName",
                        value = it.value
                    )
                )
            }.launchIn(this)

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
        if (tag !in configuration.tags.keys) error("Tag $tag not found")
        return values[tag] ?: ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST)
    }

    override suspend fun readTagQuality(tag: String): TagState {
        if (tag !in configuration.tags.keys) error("Tag $tag not found")
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
    private suspend fun readAllProperties(properties: List<Map.Entry<String, TagTableColumn>>): Unit =
        supervisorScope {
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

                                        val newValue = processTag(tag, column, values[tag], rawValue)
                                        values[tag] = newValue

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
                        logger.error(ex) { "Failed to read multiple properties ${entries.map { it.key }}" }
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
            configuration.tags.entries.groupBy { it.value.timer }
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
        sources.values.forEach { it.stop() }
    }

    override val tags: Map<String, MetaDescriptor> by lazy {
        configuration.tags.mapValues { MetaDescriptor() }
    }

    private val propertyColumnHeaders: List<ColumnHeader<Meta>> = configuration.tags.map { (name, property) ->
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
        withTagQuality: Boolean
    ): TimeSeriesRows<Meta> {
        val headers = if (withTagQuality) {
            tableHeaders + propertyColumnHeaders.map {
                SimpleColumnHeader<Meta>(it.name + TagState.TAG_QUALITY_SUFFIX, typeOf<Meta>(), Meta.EMPTY)
            }
        } else tableHeaders

        val rowFlow: SharedFlow<TimeSeriesValues<Meta>> = flow {
            while (true) {
                val values = if (withTagQuality) {
                    propertyColumnHeaders.associate { it.name to readTag(it.name) } +
                            propertyColumnHeaders.associate {
                                (it.name + TagState.TAG_QUALITY_SUFFIX) to readTagQuality(
                                    it.name
                                ).value
                            }
                } else {
                    propertyColumnHeaders.associate { it.name to readTag(it.name) }
                }
                emit(ValueWithTime(values, clock.now()))
                delay(interval)
            }
        }.shareIn(this, SharingStarted.WhileSubscribed())
        return TimeSeriesRowsFlow(headers, rowFlow)
    }

    private val stateCache = mutableMapOf<String, ValueState<Meta>>()

    /**
     * Create or get cached [ValueState] for a property of a [TagTable]. Only one [ValueState] with a given tag exists for the table
     */
    override fun subscribe(tag: String): ValueState<Meta> = stateCache.getOrPut(tag) {
        TagTableValueState(this, tag)
    }
}
