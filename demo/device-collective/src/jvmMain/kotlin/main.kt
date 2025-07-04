@file:OptIn(ExperimentalFoundationApi::class, ExperimentalSplitPaneApi::class, DFExperimental::class)

package space.kscience.controls.demo.collective

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.client.*
import space.kscience.controls.compose.conditional
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextBuilder
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.parseAsName
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.subscribe
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.maps.compose.MapView
import space.kscience.maps.compose.OpenStreetMapTileProvider
import space.kscience.maps.coordinates.Gmc
import space.kscience.maps.coordinates.meters
import space.kscience.maps.features.*
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Composable
fun rememberContext(name: String, contextBuilder: ContextBuilder.() -> Unit = {}): Context = remember {
    Context(name, contextBuilder)
}

internal val gmcMetaConverter = MetaConverter.serializable<Gmc>()
internal val gmcVelocityMetaConverter = MetaConverter.serializable<GmcVelocity>()

@Composable
fun App() {
    val scope = rememberCoroutineScope()

    val parentContext = rememberContext("Parent") {
        plugin(DeviceManager)
    }

    val collectiveModel = remember {
        generateModel(parentContext, 100, reportInterval = 1.seconds)
    }

    val roster = remember {
        collectiveModel.roster
    }

    val client = remember { CompletableDeferred<MagixEndpoint>() }

    val devices = remember { mutableStateMapOf<CollectiveDeviceId, DeviceClient>() }

    LaunchedEffect(collectiveModel) {
        launchCollectiveMagixServer(collectiveModel)

        withContext(Dispatchers.IO) {
            val magixClient = MagixEndpoint.rSocketWithWebSockets("localhost")

            client.complete(magixClient)

            collectiveModel.roster.forEach { (id, config) ->
                scope.launch {
                    val deviceClient = magixClient.remoteDevice(parentContext, "listener", id, id.parseAsName())
                    devices[id] = deviceClient
                }
            }
        }

    }

    var selectedDeviceId by remember { mutableStateOf<CollectiveDeviceId?>(null) }

    var currentPosition by remember { mutableStateOf<Gmc?>(null) }

    LaunchedEffect(selectedDeviceId, devices) {
        selectedDeviceId?.let { devices[it] }?.propertyFlow(CollectiveDevice.position)?.collect {
            currentPosition = it
        }
    }

    var showOnlyVisible by remember { mutableStateOf(false) }

    var movementProgram: Job? by remember { mutableStateOf(null) }

    val trawler: CollectiveDeviceConstructor = remember {
        collectiveModel.createTrawler(Gmc.ofDegrees(55.925, 37.50))
    }

    HorizontalSplitPane(
        splitPaneState = rememberSplitPaneState(0.9f)
    ) {
        first(400.dp) {
            var clickPoint by remember { mutableStateOf<Gmc?>(null) }

            CursorDropdownMenu(clickPoint != null, { clickPoint = null }) {
                clickPoint?.let { point ->
                    TextButton({
                        trawler.moveTo(point)
                        clickPoint = null
                    }) {
                        Text("Move trawler here")
                    }
                }
            }

            MapView(
                mapTileProvider = remember {
                    OpenStreetMapTileProvider(
                        client = HttpClient(CIO),
                        cacheDirectory = Path.of("mapCache")
                    )
                },
                config = ViewConfig(
                    onClick = { event, point ->
                        if (event.buttons.isSecondaryPressed) {
                            clickPoint = point.focus
                        }
                    }
                )
            ) {
                //draw real positions
                collectiveModel.deviceStates.forEach { device ->
                    circle(device.position.value, id = device.id + ".position").color(Color.Red)
                    device.position.valueFlow.sample(50.milliseconds).onEach {
                        val activeDevice = selectedDeviceId?.let { devices[it] }
                        val color = if (selectedDeviceId == device.id) {
                            Color.Magenta
                        } else if (
                            showOnlyVisible &&
                            activeDevice != null &&
                            device.id in activeDevice.request(CollectiveDevice.visibleNeighbors)
                        ) {
                            Color.Cyan
                        } else {
                            Color.Red
                        }

                        circle(
                            device.position.value,
                            id = device.id + ".position",
                            size = if (selectedDeviceId == device.id) 6.dp else 3.dp
                        )
                            .color(color)
                            .modifyAttribute(ZAttribute, 10f)
                            .modifyAttribute(AlphaAttribute, if (selectedDeviceId == device.id) 1f else 0.5f)
                            .modifyAttribute(AlphaAttribute, 0.5f) // does not work right now
                    }.launchIn(scope)
                }

                //draw received data
                scope.launch {
                    client.await().subscribe(DeviceManager.magixFormat).onEach { (magixMessage, deviceMessage) ->
                        if (deviceMessage is PropertyChangedMessage && deviceMessage.property == "position") {
                            val id = magixMessage.sourceEndpoint
                            val position = gmcMetaConverter.read(deviceMessage.value)

                            rectangle(
                                position,
                                id = id,
                            ).color(Color.Blue).onClick { selectedDeviceId = id }
                        }
                    }.launchIn(scope)

                }

                // draw trawler

                trawler.position.valueFlow.onEach {
                    circle(it, id = "trawler").color(Color.Black)
                }.launchIn(scope)
            }
        }
        second(200.dp) {

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Button(
                    onClick = {
                        if (movementProgram == null) {
                            //start movement program
                            movementProgram = parentContext.launch {
                                devices.values.forEach { device ->
                                    device.moveInCircles(this)
                                }
                            }
                        } else {
                            movementProgram?.cancel()
                            parentContext.launch {
                                devices.values.forEach { device ->
                                    device.write(CollectiveDevice.velocity, GmcVelocity.zero)
                                }
                            }
                            movementProgram = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (movementProgram == null) {
                        Text("Move")
                    } else {
                        Text("Stop")
                    }
                }

                collectiveModel.roster.forEach { (id, _) ->
                    Card(
                        elevation = 16.dp,
                        modifier = Modifier.padding(8.dp).onClick {
                            selectedDeviceId = id
                        }.conditional(id == selectedDeviceId) {
                            border(2.dp, Color.Blue)
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (devices[id] == null) {
                                    CircularProgressIndicator()
                                }
                                Text(
                                    text = id,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                )
                            }
                            if (id == selectedDeviceId) {
                                roster[id]?.let {
                                    Text("Meta:", color = Color.Blue, fontWeight = FontWeight.Bold)
                                    Card(elevation = 16.dp, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(it.toString())
                                    }
                                }

                                currentPosition?.let { currentPosition ->
                                    Text(
                                        "Широта: ${String.format("%.3f", currentPosition.latitude.toDegrees().value)}"
                                    )
                                    Text(
                                        "Долгота: ${String.format("%.3f", currentPosition.longitude.toDegrees().value)}"
                                    )
                                    currentPosition.elevation?.let {
                                        Text("Высота: ${String.format("%.1f", it.meters)} м")
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Показать только видимые")
                                    Checkbox(showOnlyVisible, { showOnlyVisible = it })
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}


fun main() = application {
//    System.setProperty(IO_PARALLELISM_PROPERTY_NAME, 300.toString())
    Window(onCloseRequest = ::exitApplication, title = "Maps-kt demo", icon = painterResource("SPC-logo.png")) {
        MaterialTheme {
            App()
        }
    }
}