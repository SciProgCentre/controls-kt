package space.kscience.controls.tagtable.export

import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.read
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.tagtable.TagState
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.controls.instant
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.toMeta
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.tables.*
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.reflect.typeOf


public sealed interface ExportColumn

public data class DevicePropertyExportColumn(
    val device: Name,
    val property: String,
) : ExportColumn

public data class TagExportColumn(
    val tagTable: Name,
    val tag: String,
) : ExportColumn


public class TableExporter(
    private val tagTables: Map<Name, TableStorageIndex>,
    private val messageStorage: DeviceMessageStorage
) {

    public suspend fun export(
        timeRange: ClosedRange<Instant>,
        minimalRowDistance: Duration = Duration.ZERO,
        columns: List<ExportColumn>
    ): Table<Meta> {
        //LLM generated code based on export.md

        val events = mutableListOf<Pair<String, ValueWithTime<Meta>>>()
        val outputColumns = mutableListOf<String>()
        val rowsByTagTable = mutableMapOf<Name, Rows<Meta>>()

        for (column in columns) {
            when (column) {
                is TagExportColumn -> {
                    val index = tagTables[column.tagTable]
                        ?: error("Tag table ${column.tagTable} is not available for export")
                    val rows = rowsByTagTable[column.tagTable] ?: index.selectRows(timeRange).also {
                        rowsByTagTable[column.tagTable] = it
                    }
                    val valueColumn = column.tag
                    val exportValueColumn = "${column.tagTable}.$valueColumn"
                    val qualityColumn = valueColumn + TagState.TAG_QUALITY_SUFFIX
                    val exportQualityColumn = "${column.tagTable}.$qualityColumn"
                    val hasQuality = !valueColumn.endsWith(TagState.TAG_QUALITY_SUFFIX) &&
                            rows.headers.any { it.name == qualityColumn }

                    outputColumns += exportValueColumn
                    if (hasQuality) outputColumns += exportQualityColumn

                    for (row in rows.rowSequence()) {
                        val time = row[TagTable.timeColumnHeader].instant ?: continue
                        row.getOrNull(valueColumn)?.let { value ->
                            events += exportValueColumn to ValueWithTime(value, time)
                        }
                        if (hasQuality) {
                            row.getOrNull(qualityColumn)?.let { quality ->
                                events += exportQualityColumn to ValueWithTime(quality, time)
                            }
                        }
                    }
                }

                is DevicePropertyExportColumn -> {
                    val exportColumn = "${column.device}.${column.property}"
                    outputColumns += exportColumn
                    messageStorage.read<PropertyChangedMessage>(timeRange, sourceDevice = column.device)
                        .collect { message ->
                            if (message.property == column.property) {
                                events += exportColumn to ValueWithTime(message.value, message.time)
                            }
                        }
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