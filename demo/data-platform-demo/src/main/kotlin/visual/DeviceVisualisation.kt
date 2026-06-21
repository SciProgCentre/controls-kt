/* LLM generated code: Main visualization component for devices and properties */
package space.kscience.controls.demo.visual

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.compose.letsplot.PlotDeviceProperty
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.dataforge.names.Name

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
                TimeSeriesPlot(
                    modifier = Modifier.fillMaxSize(),
                    xAxisTitle = "Time",
                    yAxisTitle = "Value",
                ) {
                    selectedProperties.forEach { (deviceName, propertyName) ->
                        val device = remember(hub, deviceName, selectedProperties) { hub.resolveDevice(deviceName) }
                        PlotDeviceProperty(
                            device = device,
                            propertyName = propertyName,
                        )
                    }
                }
            }
        }
    }
}
