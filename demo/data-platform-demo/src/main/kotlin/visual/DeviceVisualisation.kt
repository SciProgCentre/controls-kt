/* LLM generated code: Main visualization component for devices and properties */
package space.kscience.controls.demo.visual

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.compose.letsplot.PlotDeviceProperty
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.ExportColumn
import space.kscience.controls.storage.export
import space.kscience.controls.storage.propertyHistory
import space.kscience.controls.tagtable.TagState
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.toJson
import space.kscience.dataforge.names.Name
import space.kscience.tables.Table
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/* LLM generated code: Adds CSV export for selected tags and their source state. */
@Composable
fun DeviceVisualisation(
    hub: DeviceTree,
    messageStorage: DeviceMessageStorage,
    exportColumnMapping: Map<ExportColumn, ExportColumn>,
    sourceStateColumns: List<ExportColumn>,
    exportStartTime: Instant,
    modifier: Modifier = Modifier
) {
    var selectedProperties by remember { mutableStateOf(setOf<ExportColumn>()) }
    var exportStatus by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = modifier.fillMaxSize()) {
        // Left Panel: Navigation Tree
        Surface(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            tonalElevation = 1.dp
        ) {
            DeviceTree(
                node = hub,
                selectedProperties = selectedProperties,
                onSelectProperty = { deviceName, propertyName, selected ->
                    selectedProperties = if (selected) {
                        selectedProperties + ExportColumn(deviceName, propertyName)
                    } else {
                        selectedProperties - ExportColumn(deviceName, propertyName)
                    }
                },
                modifier = Modifier.padding(8.dp)
            )
        }

        // Central Area: Plot
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !exporting,
                    onClick = {
                        coroutineScope.launch {
                            exporting = true
                            exportStatus = "Exporting selected tags..."
                            try {
                                val tagColumns = selectedProperties.flatMap { column ->
                                    val column = exportColumnMapping[column] ?: return@flatMap emptyList()
                                    listOf(
                                        column,
                                        column.copy(
                                            property = column.property + TagState.TAG_QUALITY_SUFFIX,
                                            extractionPath = Name.of("quality"),
                                            exportColumnName = column.exportColumnName + TagState.TAG_QUALITY_SUFFIX,
                                        ),
                                    )
                                }
                                val table = messageStorage.export(
                                    timeRange = exportStartTime..Clock.System.now(),
                                    minimalRowDistance = 1.seconds,
                                    columns = tagColumns + sourceStateColumns,
                                )
                                val csv = table.toCsv()
                                val exportDirectory = Path("data").createDirectories()
                                val exportPath = exportDirectory.resolve(
                                    "export-${Clock.System.now().toString().replace(":", "-")}.csv"
                                )
                                exportPath.writeText(csv)

                                exportStatus = "Exported to $exportPath. Recently buffered data may not be stored yet."
                            } catch (error: Exception) {
                                exportStatus = "Export failed: ${error.message}"
                            }
                            exporting = false
                        }
                    }
                ) {
                    Text(if (exporting) "Exporting..." else "Export")
                }
                if (exportStatus.isNotEmpty()) {
                    Text(exportStatus, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                if (selectedProperties.isEmpty()) {
                    Text(
                        text = "Select properties from the tree to visualize",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    TimeSeriesPlot(
                        modifier = Modifier.fillMaxSize(),
                        xAxisTitle = "Time",
                        yAxisTitle = "Value",
                    ) {
                        selectedProperties.forEach { column ->
                            val device =
                                remember(hub, column.device, selectedProperties) { hub.resolveDevice(column.device) }
                            val history = exportColumnMapping[column]?.let { historyColumn ->
                                messageStorage.propertyHistory(
                                    historyColumn.property,
                                    MetaConverter.meta,
                                    deviceName = historyColumn.device
                                )
                            }
                            PlotDeviceProperty(
                                device = device,
                                propertyName = column.property,
                                seriesName = column.exportColumnName,
                                history = history
                            )
                        }
                    }
                }
            }
        }
    }
}

/* LLM generated code: Writes exported table metadata values to escaped CSV fields. */
private fun Table<Meta>.toCsv(): String = buildString {
    appendLine(headers.joinToString(",") { it.name.toCsvField() })
    rowSequence().forEach { row ->
        appendLine(headers.joinToString(",") { header ->
            row.getOrNull(header.name)?.toJson()?.let { value ->
                (value as? JsonPrimitive)?.content ?: value.toString()
            }.orEmpty().toCsvField()
        })
    }
}

private fun String.toCsvField(): String = "\"${replace("\"", "\"\"")}\""
