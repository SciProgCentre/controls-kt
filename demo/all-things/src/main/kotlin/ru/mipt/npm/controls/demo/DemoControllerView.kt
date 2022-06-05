package ru.mipt.npm.controls.demo

import io.ktor.server.engine.ApplicationEngine
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.launch
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import ru.mipt.npm.controls.client.connectToMagix
import ru.mipt.npm.controls.demo.DemoDevice.Companion.cosScale
import ru.mipt.npm.controls.demo.DemoDevice.Companion.sinScale
import ru.mipt.npm.controls.demo.DemoDevice.Companion.timeScale
import ru.mipt.npm.controls.manager.DeviceManager
import ru.mipt.npm.controls.manager.install
import ru.mipt.npm.controls.opcua.server.OpcUaServer
import ru.mipt.npm.controls.opcua.server.endpoint
import ru.mipt.npm.controls.opcua.server.serveDevices
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithTcp
import ru.mipt.npm.magix.rsocket.rSocketWithWebSockets
import ru.mipt.npm.magix.server.startMagixServer
import space.kscience.dataforge.context.*
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class DemoController : Controller(), ContextAware {

    var device: DemoDevice? = null
    var magixServer: ApplicationEngine? = null
    var visualizer: ApplicationEngine? = null
    var opcUaServer: OpcUaServer = OpcUaServer {
        setApplicationName(LocalizedText.english("ru.mipt.npm.controls.opcua"))
        endpoint {
            setBindPort(9999)
            //use default endpoint
        }
    }

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.fetch(DeviceManager)

    fun init() {
        context.launch {
            device = deviceManager.install("demo", DemoDevice)
            //starting magix event loop
            magixServer = startMagixServer(enableRawRSocket = true, enableZmq = true)
            //Launch device client and connect it to the server
            val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost")
            deviceManager.connectToMagix(deviceEndpoint)
            val visualEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")
            visualizer = visualEndpoint.startDemoDeviceServer()

            opcUaServer.startup()
            opcUaServer.serveDevices(deviceManager)
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
        device?.close()
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
                        timeScale.write(timeScaleSlider.value)
                        sinScale.write(xScaleSlider.value)
                        cosScale.write(yScaleSlider.value)
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