package space.kscience.controls.demo.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.spec.propertyFlow
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.maps.compose.MapView
import space.kscience.maps.compose.OpenStreetMapTileProvider
import space.kscience.maps.features.ViewConfig
import space.kscience.maps.features.circle
import space.kscience.maps.features.color
import space.kscience.maps.features.rectangle
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

    val devices: Map<RemoteDeviceId, RemoteDevice> = remember {
        collectiveModel.deviceStates.associate {
            val device = RemoteDeviceConstructor(deviceManager.context, it.configuration, it.position, it.velocity)
            device.moveInCircles()
            it.id to device
        }
    }

    val mapTileProvider = remember {
        OpenStreetMapTileProvider(
            client = HttpClient(CIO),
            cacheDirectory = Path.of("mapCache")
        )
    }

    MapView(
        mapTileProvider = mapTileProvider,
        config = ViewConfig()
    ) {
        collectiveModel.deviceStates.forEach { device ->
            circle(device.position.value, id = device.id + ".position").color(Color.Red)
            device.position.valueFlow.onEach {
                circle(device.position.value, id = device.id + ".position").color(Color.Red)
            }.launchIn(scope)
        }

        devices.forEach { (id, device) ->
            device.propertyFlow(RemoteDevice.position).onEach {  position ->
                rectangle(position, id = id).color(Color.Blue)
            }.launchIn(scope)
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