package space.kscience.controls.demo

import io.ktor.server.engine.ApplicationEngine
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.GetDescriptionMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.client.launchMagixService
import space.kscience.controls.client.magixFormat
import space.kscience.controls.demo.DemoDevice.Companion.cosScale
import space.kscience.controls.demo.DemoDevice.Companion.sinScale
import space.kscience.controls.demo.DemoDevice.Companion.timeScale
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.controls.spec.write
import space.kscience.dataforge.context.*
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.send
import space.kscience.magix.api.subscribe
import space.kscience.magix.rsocket.rSocketWithTcp
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.RSocketMagixFlowPlugin
import space.kscience.magix.server.startMagixServer
import space.kscince.magix.zmq.ZmqMagixFlowPlugin
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class DemoController : Controller(), ContextAware {

    var device: DemoDevice? = null
    var magixServer: ApplicationEngine? = null
    var visualizer: ApplicationEngine? = null
    var opcUaServer: OpcUaServer = OpcUaServer {
        setApplicationName(LocalizedText.english("space.kscience.controls.opcua"))

        endpoint {
            setBindPort(4840)
            //use default endpoint
        }
    }

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.request(DeviceManager)


    fun init() {
        context.launch {
            device = deviceManager.install("demo", DemoDevice)
            //starting magix event loop
            magixServer = startMagixServer(
                RSocketMagixFlowPlugin(), //TCP rsocket support
                ZmqMagixFlowPlugin() //ZMQ support
            )
            //Launch a device client and connect it to the server
            val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost")
            deviceManager.launchMagixService(deviceEndpoint)
            //connect visualization to a magix endpoint
            val visualEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")
            visualizer = startDemoDeviceServer(visualEndpoint)

            //serve devices as OPC-UA namespace
            opcUaServer.startup()
            opcUaServer.serveDevices(deviceManager)


            val listenerEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")
            listenerEndpoint.subscribe(DeviceManager.magixFormat).onEach { (_, deviceMessage)->
                // print all messages that are not property change message
                if(deviceMessage !is PropertyChangedMessage){
                    println(">> ${Json.encodeToString(DeviceMessage.serializer(), deviceMessage)}")
                }
            }.launchIn(this)
            listenerEndpoint.send(DeviceManager.magixFormat, GetDescriptionMessage(), "listener", "controls-kt")

        }
    }

    fun shutdown() {
        logger.info { "Shutting down..." }
        opcUaServer.shutdown()
        logger.info { "OpcUa server stopped" }
        visualizer?.stop(1000, 5000)
        logger.info { "Visualization server stopped" }
        magixServer?.stop(1000, 5000)
        logger.info { "Magix server stopped" }
        device?.stop()
        logger.info { "Device server stopped" }
        context.close()
    }
}


class DemoControllerView : View(title = " Demo controller remote") {
    private val controller: DemoController by inject()
    private var timeScaleSlider: Slider by singleAssign()
    private var xScaleSlider: Slider by singleAssign()
    private var yScaleSlider: Slider by singleAssign()

    override val root: Parent = vbox {
        hbox {
            label("Time scale")
            pane {
                hgrow = Priority.ALWAYS
            }
            timeScaleSlider = slider(1000..10000, 5000) {
                isShowTickLabels = true
                isShowTickMarks = true
            }
        }
        hbox {
            label("X scale")
            pane {
                hgrow = Priority.ALWAYS
            }
            xScaleSlider = slider(0.1..2.0, 1.0) {
                isShowTickLabels = true
                isShowTickMarks = true
            }
        }
        hbox {
            label("Y scale")
            pane {
                hgrow = Priority.ALWAYS
            }
            yScaleSlider = slider(0.1..2.0, 1.0) {
                isShowTickLabels = true
                isShowTickMarks = true
            }
        }
        button("Submit") {
            useMaxWidth = true
            action {
                controller.device?.run {
                    launch {
                        write(timeScale, timeScaleSlider.value)
                        write(sinScale, xScaleSlider.value)
                        write(cosScale, yScaleSlider.value)
                    }
                }
            }
        }
        button("Show plots") {
            useMaxWidth = true
            action {
                controller.visualizer?.run {
                    val host = "localhost"//environment.connectors.first().host
                    val port = environment.connectors.first().port
                    val uri = URI("http", null, host, port, "/", null, null)
                    Desktop.getDesktop().browse(uri)
                }
            }
        }
    }
}


class DemoControllerApp : App(DemoControllerView::class) {
    private val controller: DemoController by inject()

    override fun start(stage: Stage) {
        super.start(stage)
        controller.init()
    }

    override fun stop() {
        controller.shutdown()
        super.stop()
    }
}


fun main() {
    launch<DemoControllerApp>()
}