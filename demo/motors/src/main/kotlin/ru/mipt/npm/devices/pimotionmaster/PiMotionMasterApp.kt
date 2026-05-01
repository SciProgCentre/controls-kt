package ru.mipt.npm.devices.pimotionmaster


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installing
import space.kscience.controls.spec.execute
import space.kscience.controls.spec.read
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta

//class PiMotionMasterApp : App(PiMotionMasterView::class)
//
//class PiMotionMasterController : Controller() {
//    //initialize context
//    val context = Context("piMotionMaster") {
//        plugin(DeviceManager)
//    }
//
//    //initialize deviceManager plugin
//    val deviceManager: DeviceManager = context.request(DeviceManager)
//
//    // install device
//    val motionMaster: PiMotionMasterDevice by deviceManager.installing(PiMotionMasterDevice)
//}

@Composable
fun ColumnScope.piMotionMasterAxis(
    axisName: String,
    axis: Device,
) {
    var min by remember { mutableStateOf(0f) }
    var max by remember { mutableStateOf(1f) }
    var targetPosition by remember { mutableStateOf(0f) }
    val position: Double by axis.composeState(AxisSpec.position, 0.0)

    val scope = rememberCoroutineScope()

    LaunchedEffect(axis) {
        min = axis.read(AxisSpec.minPosition).toFloat()
        max = axis.read(AxisSpec.maxPosition).toFloat()
        targetPosition = axis.read(AxisSpec.position).toFloat()
    }


    Row {
        Text(axisName)

        Column {
            Slider(
                value = position.toFloat(),
                enabled = false,
                onValueChange = { },
                valueRange = min..max
            )
            Slider(
                value = targetPosition,
                onValueChange = { newPosition ->
                    targetPosition = newPosition
                    scope.launch {
                        axis.execute(AxisSpec.move, Meta(newPosition))
                    }
                },
                valueRange = min..max
            )

        }
    }
}

@Composable
fun AxisPane(axes: Map<String, Device>) {
    Column {
        axes.forEach { (name, axis) ->
            this.piMotionMasterAxis(name, axis)
        }
    }
}


@Composable
fun PiMotionMasterApp(device: PiMotionMasterDevice) {

//    val scope = rememberCoroutineScope()
    val connected by device.composeState(PiMotionMasterDevice.connected, false)
    var debugServerJob by remember { mutableStateOf<Job?>(null) }
    var axes by remember { mutableStateOf<Map<String, Axis>?>(null) }
    //private val axisList = FXCollections.observableArrayList<Map.Entry<String, PiMotionMasterDevice.Axis>>()
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf(10024) }

    Scaffold {
        Column {


            Text("Address:")
            Row {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    enabled = debugServerJob == null,
                    modifier = Modifier.weight(1f)
                )
                var portError by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = port.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { value ->
                            port = value
                            portError = false
                        } ?: run {
                            portError = true
                        }
                    },
                    label = { Text("Port") },
                    enabled = debugServerJob == null,
                    isError = portError,
                    modifier = Modifier.weight(1f),
                )
            }
            Row {
                Button(
                    onClick = {
                        if (debugServerJob == null) {
                            debugServerJob = device.context.launchPiDebugServer(port, listOf("1", "2", "3", "4"))
                        } else {
                            debugServerJob?.cancel()
                            debugServerJob = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (debugServerJob == null) {
                        Text("Start debug server")
                    } else {
                        Text("Stop debug server")
                    }
                }
            }
            Row {
                Button(
                    onClick = {
                        if (!connected) {
                            device.launch {
                                device.connect(host, port)
                                axes = device.axes
                            }
                        } else {
                            device.launch {
                                device.disconnect()
                                axes = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!connected) {
                        Text("Connect")
                    } else {
                        Text("Disconnect")
                    }
                }
            }

            axes?.let { axes ->
                AxisPane(axes)
            }
        }
    }
}


fun main() = application {

    val context = Context("piMotionMaster") {
        plugin(DeviceManager)
    }

    //initialize deviceManager plugin
    val deviceManager: DeviceManager = context.request(DeviceManager)

    // install device
    val motionMaster: PiMotionMasterDevice by deviceManager.installing(PiMotionMasterDevice)

    Window(
        title = "Pi motion master demo",
        onCloseRequest = { exitApplication() },
        state = rememberWindowState(width = 400.dp, height = 300.dp)
    ) {
        MaterialTheme {
            PiMotionMasterApp(motionMaster)
        }
    }
}