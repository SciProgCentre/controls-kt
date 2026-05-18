/* LLM generated code: Main visualization component for devices and properties */
@file:OptIn(ExperimentalKoalaPlotApi::class)
package space.kscience.controls.demo.visual

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberDoubleLinearAxisModel
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.compose.koala.PlotDeviceProperty
import space.kscience.controls.compose.koala.TimeAxisModel
import space.kscience.dataforge.names.Name
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val colors = listOf(
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Magenta,
    Color.Cyan,
    Color(0xFFFFA500), // Orange
    Color(0xFF800080), // Purple
    Color(0xFF008000), // Dark Green
)

@Composable
fun DeviceVisualisation(
    hub: DeviceTree,
    modifier: Modifier = Modifier
) {
    var selectedProperties by remember { mutableStateOf(setOf<Pair<Name, String>>()) }

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
                        selectedProperties + (deviceName to propertyName)
                    } else {
                        selectedProperties - (deviceName to propertyName)
                    }
                },
                modifier = Modifier.padding(8.dp)
            )
        }

        // Central Area: Plot
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            if (selectedProperties.isEmpty()) {
                Text(
                    text = "Select properties from the tree to visualize",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {

                ChartLayout(
//                    legend = {
//                        ColumnLegend2(
//                            itemCount = selectedProperties.size,
//                            symbol = { index ->
//                                Box(modifier = Modifier.size(12.dp).background(colors[index % colors.size]))
//                            },
//                            label = { index ->
//                                val (deviceName, propertyName) = selectedProperties.toList()[index]
//                                val fullName = if (deviceName.isEmpty()) propertyName else "$deviceName.$propertyName"
//                                Text(fullName, style = MaterialTheme.typography.labelSmall)
//                            }
//                        )
//                    }
                ) {
                    XYGraph<Instant, Double>(
                        xAxisModel = remember { TimeAxisModel.recent(30.seconds) },
                        yAxisModel = rememberDoubleLinearAxisModel(0.0..1.0),
                        modifier = Modifier.fillMaxSize(),
                        xAxisTitle = "Time",
                        yAxisTitle = "Value",
                        xAxisLabels = { it.toString() },
                        yAxisLabels = { it.toString() },
                    ) {
                        selectedProperties.forEachIndexed { index, property ->
                            val (deviceName, propertyName) = property
                            val device = remember(hub, deviceName) { hub.resolveDevice(deviceName) }
                            PlotDeviceProperty(
                                device = device,
                                propertyName = propertyName,
                                lineStyle = LineStyle(SolidColor(colors[index % colors.size]), strokeWidth = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
