package space.kscience.controls.storage

import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.toMeta
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
import space.kscience.tables.*
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * @param device The name of the device associated with the column.
 * @param property The name of the property associated with the column.
 * @param extractionPath The path to the data inside column meta.
 * @param exportColumnName The name of column in export table.
 */
public data class ExportColumn(
    val device: Name,
    val property: String,
    val extractionPath: Name = Name.EMPTY,
    val exportColumnName: String = "$device/$property${if (extractionPath.isEmpty()) "" else "/$extractionPath"}",
)


/**
 * Handles the export of tabular data from multiple sources within a given time range. The exported data is
 * compiled into a table format, combining values from tag tables and device message properties.
 *
 * @constructor Creates a new instance of TableExporter with the given tag table index and message storage.
 * @receiver The storage for device messages, used for retrieving device property changes.
 */
public suspend fun DeviceMessageStorage.export(
    timeRange: ClosedRange<Instant>,
    minimalRowDistance: Duration = Duration.ZERO,
    columns: List<ExportColumn>
):Table<Meta>{

    val events = mutableListOf<Pair<String, ValueWithTime<Meta>>>()
    val outputColumns = columns.map { it.exportColumnName }

    read<PropertyChangedMessage>(timeRange).collect { message ->
        columns.forEach { column ->
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