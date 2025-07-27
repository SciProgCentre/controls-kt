@file:OptIn(ExperimentalTime::class)

package space.kscience.controls.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.GetDescriptionMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.client.magixFormat
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.controls.spec.write
import space.kscience.dataforge.context.*
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.send
import space.kscience.magix.api.subscribe
import space.kscience.magix.rsocket.rSocketWithTcp
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.RSocketMagixFlowPlugin
import space.kscience.magix.server.startMagixServer
import space.kscince.magix.zmq.ZmqMagixFlowPlugin
import java.awt.Desktop
import java.net.URI
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


private val json = Json { prettyPrint = true }

class DemoController : ContextAware {

    var device: DemoDevice? = null
    var magixServer: EmbeddedServer<*, *>? = null
    var visualizer: EmbeddedServer<*, *>? = null

    val opcUaServer: OpcUaServer = OpcUaServer {
        setApplicationName(LocalizedText.english("space.kscience.controls.opcua"))

        endpoint {
            setBindPort(9091)
            //use default endpoint
        }
    }

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.request(DeviceManager)


    fun start(): Job = context.launch {
        device = deviceManager.install("demo", DemoDevice).apply {
            write(DemoDevice.comment, "Device initialized")
        }

        //starting magix event loop
        magixServer = startMagixServer(
            RSocketMagixFlowPlugin(), //TCP rsocket support
            ZmqMagixFlowPlugin() //ZMQ support
        )
        //Launch a device client and connect it to the server
        val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost")
        deviceManager.launchMagixService(deviceEndpoint, "demoDevice")
        //connect visualization to a magix endpoint
        val visualEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")
        visualizer = startDemoDeviceServer(visualEndpoint)

        //serve devices as OPC-UA namespace
        opcUaServer.serveDevices(this, deviceManager)
        opcUaServer.startup()

        //create a remote listener endpoint
        val listenerEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

        // subscribe remote endpoint
        listenerEndpoint.subscribe(DeviceManager.magixFormat).onEach { (magixMessage, deviceMessage) ->
            // print all messages that are not property change message
            if (deviceMessage !is PropertyChangedMessage) {
                println(">> ${json.encodeToString(MagixMessage.serializer(), magixMessage)}")
            }
        }.launchIn(this)

        // send description request
        listenerEndpoint.send(
            format = DeviceManager.magixFormat,
            payload = GetDescriptionMessage(Clock.System.now()),
            source = "listener",
//            target = "demoDevice"
        )
    }

    fun shutdown(): Job = context.launch {
        logger.info { "Shutting down..." }
        opcUaServer.shutdown()
        logger.info { "OpcUa server stopped" }
        visualizer?.stop(1000, 5000)
        logger.info { "Visualization server stopped" }
        magixServer?.stop(1000, 5000)
        logger.info { "Magix server stopped" }
        device?.stop()
        logger.info { "Device server stopped" }
    }
}

@Composable
fun DemoControls(controller: DemoController) {
    var timeScale by remember { mutableStateOf(5000f) }
    var xScale by remember { mutableStateOf(1f) }
    var yScale by remember { mutableStateOf(1f) }

    Surface(Modifier.padding(5.dp)) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text("Time Scale", modifier = Modifier.align(Alignment.CenterVertically).width(100.dp))
                TextField(
                    String.format("%.2f", timeScale),
                    {},
                    enabled = false,
                    modifier = Modifier.align(Alignment.CenterVertically).width(100.dp)
                )
                Slider(timeScale, onValueChange = { timeScale = it }, steps = 20, valueRange = 1000f..5000f)
            }
            Row(Modifier.fillMaxWidth()) {
                Text("X scale", modifier = Modifier.align(Alignment.CenterVertically).width(100.dp))
                TextField(
                    String.format("%.2f", xScale),
                    {},
                    enabled = false,
                    modifier = Modifier.align(Alignment.CenterVertically).width(100.dp)
                )
                Slider(xScale, onValueChange = { xScale = it }, steps = 20, valueRange = 0.1f..2.0f)
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Y scale", modifier = Modifier.align(Alignment.CenterVertically).width(100.dp))
                TextField(
                    String.format("%.2f", yScale),
                    {},
                    enabled = false,
                    modifier = Modifier.align(Alignment.CenterVertically).width(100.dp)
                )
                Slider(yScale, onValueChange = { yScale = it }, steps = 20, valueRange = 0.1f..2.0f)
            }
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        controller.device?.run {
                            launch {
                                write(DemoDevice.timeScale, timeScale.toDouble())
                                write(DemoDevice.sinScale, xScale.toDouble())
                                write(DemoDevice.cosScale, yScale.toDouble())
                            }
                        }
                    },
                    Modifier.fillMaxWidth()
                ) {
                    Text("Submit")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        controller.visualizer?.run {
                            val connector = runBlocking { application.engine.resolvedConnectors().first()}
                            val host = "localhost"
                            val port = connector.port
                            val uri = URI("http", null, host, port, "/", null, null)
                            Desktop.getDesktop().browse(uri)
                        }
                    },
                    Modifier.fillMaxWidth()
                ) {
                    Text("Show plots")
                }
            }
        }

    }
}


fun main() = application {
    val controller = remember { DemoController().apply { start() } }

    Window(
        title = "All things control",
        onCloseRequest = {
            controller.shutdown()
            exitApplication()
        },
        state = rememberWindowState(width = 400.dp, height = 320.dp)
    ) {
        MaterialTheme {
            DemoControls(controller)
        }
    }
}