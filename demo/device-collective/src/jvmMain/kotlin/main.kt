@file:OptIn(ExperimentalFoundationApi::class, ExperimentalSplitPaneApi::class)

package space.kscience.controls.demo.collective

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import space.kscience.controls.compose.conditional
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.spec.useProperty
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.maps.compose.MapView
import space.kscience.maps.compose.OpenStreetMapTileProvider
import space.kscience.maps.features.*
import java.nio.file.Path


@Composable
fun rememberDeviceManager(): DeviceManager = remember {
    val context = Context {
        plugin(DeviceManager)
    }

    context.request(DeviceManager)
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()


    val deviceManager = rememberDeviceManager()


    val collectiveModel = remember {
        generateModel(deviceManager.context)
    }

    val devices: Map<DeviceId, CollectiveDevice> = remember {
        collectiveModel.deviceStates.associate {
            val device = CollectiveDeviceConstructor(
                context = deviceManager.context,
                configuration = it.configuration,
                position = it.position,
                velocity = it.velocity
            ) {
                collectiveModel.locateVisible(it.id).keys
            }
            device.moveInCircles()
            it.id to device
        }
    }

    var selectedDeviceId by remember { mutableStateOf<DeviceId?>(null) }
    var showOnlyVisible by remember { mutableStateOf(false) }

    val mapTileProvider = remember {
        OpenStreetMapTileProvider(
            client = HttpClient(CIO),
            cacheDirectory = Path.of("mapCache")
        )
    }

    HorizontalSplitPane(
        splitPaneState = rememberSplitPaneState(0.9f)
    ) {
        first(400.dp) {
            MapView(
                mapTileProvider = mapTileProvider,
                config = ViewConfig()
            ) {
                collectiveModel.deviceStates.forEach { device ->
                    circle(device.position.value, id = device.id + ".position").color(Color.Red)
                    device.position.valueFlow.onEach {
                        circle(device.position.value, id = device.id + ".position", size = 3.dp)
                            .color(Color.Red)
                            .modifyAttribute(AlphaAttribute, 0.5f) // does not work right now
                    }.launchIn(scope)
                }

                devices.forEach { (id, device) ->
                    device.useProperty(CollectiveDevice.position, scope = scope) { position ->

                        val activeDevice = selectedDeviceId?.let { devices[it] }

                        if (activeDevice == null || id == selectedDeviceId || !showOnlyVisible || id in activeDevice.listVisible()) {
                            rectangle(
                                position,
                                id = id,
                                size = if (selectedDeviceId == id) DpSize(10.dp, 10.dp) else DpSize(5.dp, 5.dp)
                            ).color(if (selectedDeviceId == id) Color.Magenta else Color.Blue)
                                .modifyAttribute(AlphaAttribute, if (selectedDeviceId == id) 1f else 0.5f)
                                .onClick { selectedDeviceId = id }
                        } else {
                            removeFeature(id)
                        }

                    }
                }
            }
        }
        second(200.dp) {
            Column {
                selectedDeviceId?.let { id ->
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .border(2.dp, Color.DarkGray)
                    ) {
                        Card(
                            elevation = 16.dp,
                        ) {
                            Text(
                                text = "Выбран: $id",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp).fillMaxWidth()
                            )
                        }
                        devices[id]?.let {
                            Text(it.meta.toString(), Modifier.padding(10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                            Text("Показать только видимые")
                            Checkbox(showOnlyVisible, { showOnlyVisible = it })
                        }
                    }
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    devices.forEach { (id, device) ->
                        Card(
                            elevation = 16.dp,
                            modifier = Modifier.padding(8.dp).onClick {
                                selectedDeviceId = id
                            }.conditional(id == selectedDeviceId) {
                                border(2.dp, Color.Blue)
                            }
                        ) {
                            Text(
                                text = id,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}


fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Maps-kt demo", icon = painterResource("SPC-logo.png")) {
        MaterialTheme {
            App()
        }
    }
}