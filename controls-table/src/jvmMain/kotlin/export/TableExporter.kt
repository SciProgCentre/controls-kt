package space.kscience.controls.tagtable.export

import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.instant
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.FileDeviceMessageStorage
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.SingleFileEnvelopeOperations
import space.kscience.controls.storage.read
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.OpcTagSourceConfig
import space.kscience.controls.tagtable.PlcTagTable
import space.kscience.controls.tagtable.TagTableConfiguration
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.toMeta
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
import space.kscience.tables.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A descriptor of a column in an export table.
 */
public sealed interface ExportColumn {
    /**
     * The name of column in export table
     */
    public val exportColumnName: String

    /**
     * The path to the data inside column meta
     */
    public val extractionPath: Name
}

public data class DevicePropertyExportColumn(
    val device: Name,
    val property: String,
    override val extractionPath: Name = Name.EMPTY,
    override val exportColumnName: String = "$device/$property${if (extractionPath.isEmpty()) "" else "/$extractionPath"}",
) : ExportColumn

public data class TagExportColumn(
    val tag: String,
    override val extractionPath: Name = Name.EMPTY,
    override val exportColumnName: String = "$tag${if (extractionPath.isEmpty()) "" else "/$extractionPath"}",
) : ExportColumn

/**
 * Build a table exporter using the storage paths in a tag-table configuration.
 */
public fun TableExporter(
    storage: ControlsStoragePlugin,
    configuration: TagTableConfiguration,
): TableExporter {
    val storageConfiguration = configuration.storage ?: error("Tag table storage is not configured")
    val messageStorageConfiguration = configuration.messageStorage ?: error("Message storage is not configured")
    val operations = if (storageConfiguration.separateMeta) {
        NativeFileEnvelopeOperations(storage.io)
    } else {
        SingleFileEnvelopeOperations(storage.io)
    }
    val tableDirectory = Path(storageConfiguration.path).createDirectories()
    val messageDirectory = Path(messageStorageConfiguration.path).createDirectories()
    val index = TableStorageIndex(storage, tableDirectory, operations = operations)
    val messages = FileDeviceMessageStorage(
        directory = messageDirectory,
        maxMessagesPerFile = messageStorageConfiguration.maxMessagesPerFile,
    )
    return TableExporter(index, messages)
}

/**
 * Describe the persisted state columns for every configured source.
 */
public fun TagTableConfiguration.sourceStateExportColumns(): List<ExportColumn> = sources.flatMap { (source, config) ->
    val device = Name.of(PlcTagTable.SOURCE_DEVICE_KEY, source)
    buildList {
        add(DevicePropertyExportColumn(device, "state", Name.of("lifeCycle")))
        if (config is OpcTagSourceConfig) {
            add(DevicePropertyExportColumn(device, "state", Name.of("sourceTimeOffset")))
        }
    }
}

/**
 * Handles the export of tabular data from multiple sources within a given time range. The exported data is
 * compiled into a table format, combining values from tag tables and device message properties.
 *
 * @constructor Creates a new instance of TableExporter with the given tag table index and message storage.
 * @param tagTableIndex The index of tag tables used for looking up data rows by time.
 * @param messageStorage The storage for device messages, used for retrieving device property changes.
 */
public class TableExporter(
    private val tagTableIndex: TableStorageIndex,
    private val messageStorage: DeviceMessageStorage
) {

    public suspend fun export(
        timeRange: ClosedRange<Instant>,
        minimalRowDistance: Duration = Duration.ZERO,
        columns: List<ExportColumn>
    ): Table<Meta> {

        if (tagTableIndex.lifecycleState != LifecycleState.STARTED) {
            tagTableIndex.start()
        }

        val events = mutableListOf<Pair<String, ValueWithTime<Meta>>>()
        val outputColumns = columns.map { it.exportColumnName }

        val tagColumns = columns.filterIsInstance<TagExportColumn>()

        tagTableIndex.selectRows(timeRange).rowSequence().forEach { row ->
            val time = row[TagTable.timeColumnHeader].instant ?: return@forEach
            tagColumns.forEach { column ->
                row.getOrNull(column.tag)?.let { value ->
                    events += column.exportColumnName to ValueWithTime(value[column.extractionPath] ?: Meta.EMPTY, time)
                }
            }
        }

        val deviceColumns = columns.filterIsInstance<DevicePropertyExportColumn>()

        messageStorage.read<PropertyChangedMessage>(timeRange).collect { message ->
            deviceColumns.forEach { column ->
                if (message.sourceDevice == column.device && message.property == column.property) {
                    events += column.exportColumnName to ValueWithTime(
                        value = message.value[column.extractionPath] ?: Meta.EMPTY,
                        time = message.time
                    )
                }
            }
        }

        val timeHeader = SimpleColumnHeader<Meta>("time", typeOf<Meta>(), Meta.EMPTY)
        val headers = listOf(timeHeader) + outputColumns.map { name ->
            SimpleColumnHeader<Meta>(name, typeOf<Meta>(), Meta.EMPTY)
        }
        val outputRows = mutableListOf<Row<Meta>>()
        val currentValues = mutableMapOf<String, Meta>()
        val sortedEvents = events.sortedBy { it.second.time }
        var lastRowTime: Instant? = null
        var eventIndex = 0

        while (eventIndex < sortedEvents.size) {
            val time = sortedEvents[eventIndex].second.time
            while (eventIndex < sortedEvents.size && sortedEvents[eventIndex].second.time == time) {
                val (column, event) = sortedEvents[eventIndex]
                currentValues[column] = event.value
                eventIndex++
            }

            val previousRowTime = lastRowTime
            if (currentValues.keys.containsAll(outputColumns) &&
                (previousRowTime == null || time - previousRowTime >= minimalRowDistance)
            ) {
                val rowValues = buildMap {
                    put(timeHeader.name, time.toMeta())
                    outputColumns.forEach { columnName -> put(columnName, currentValues.getValue(columnName)) }
                }
                outputRows += MapRow(rowValues)
                lastRowTime = time
            }
        }

        return RowTable(headers, outputRows)
    }
}