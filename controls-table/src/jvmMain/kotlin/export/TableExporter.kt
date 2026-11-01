package space.kscience.controls.tagtable.export

import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.instant
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.read
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.toMeta
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
import space.kscience.tables.*
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Instant


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


public class TableExporter(
    private val tagTableIndex: TableStorageIndex,
    private val messageStorage: DeviceMessageStorage
) {

    public suspend fun export(
        timeRange: ClosedRange<Instant>,
        minimalRowDistance: Duration = Duration.ZERO,
        columns: List<ExportColumn>
    ): Table<Meta> {

        val events = mutableListOf<Pair<String, ValueWithTime<Meta>>>()
        val outputColumns = columns.map { it.exportColumnName }

        val tagColumns = columns.filterIsInstance<TagExportColumn>()

        tagTableIndex.selectRows(timeRange).rowSequence().forEach { row ->
            val time = row[TagTable.timeColumnHeader].instant ?: return@forEach
            tagColumns.forEach {column->
                row.getOrNull(column.tag)?.let { value ->
                    events += column.tag to ValueWithTime(value[column.extractionPath] ?: Meta.EMPTY, time)
                }
            }
        }

        val deviceColumns = columns.filterIsInstance<DevicePropertyExportColumn>()

        messageStorage.read<PropertyChangedMessage>(timeRange).collect {message->
            deviceColumns.forEach {column->
                if(message.sourceDevice == column.device && message.property == column.property){
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