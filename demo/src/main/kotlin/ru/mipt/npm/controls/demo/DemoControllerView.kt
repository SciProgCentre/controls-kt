package ru.mipt.npm.controls.demo

import io.ktor.server.engine.ApplicationEngine
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.client.launchDfMagix
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.install
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithTcp
import ru.mipt.npm.magix.server.startMagixServer
import space.kscience.dataforge.context.*
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class DemoController : Controller(), ContextAware {

    var device: DemoDevice? = null
    var magixServer: ApplicationEngine? = null
    var visualizer: ApplicationEngine? = null

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.fetch(DeviceManager)

    fun init() {
        context.launch {
            device = deviceManager.install("demo", DemoDevice)
            //starting magix event loop
            magixServer = startMagixServer()
            //Launch device client and connect it to the server
            deviceManager.launchDfMagix(MagixEndpoint.rSocketWithTcp("localhost", DeviceMessage.serializer()))
            visualizer = startDemoDeviceServer()
        }
    }

    fun shutdown() {
        logger.info { "Shutting down..." }
        visualizer?.stop(1000,5000)
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
            xScaleSlider = slider(0.0..2.0, 1.0) {
                isShowTickLabels = true
                isShowTickMarks = true
            }
        }
        hbox {
            label("Y scale")
            pane {
                hgrow = Priority.ALWAYS
            }
            yScaleSlider = slider(0.0..2.0, 1.0) {
                isShowTickLabels = true
                isShowTickMarks = true
            }
        }
        button("Submit") {
            useMaxWidth = true
            action {
                controller.device?.apply {
                    timeScale = timeScaleSlider.value
                    sinScale = xScaleSlider.value
                    cosScale = yScaleSlider.value
                }
            }
        }
        button("Show plots") {
            useMaxWidth = true
            action {
                controller.magixServer?.run {
                    val host = "localhost"//environment.connectors.first().host
                    val port = environment.connectors.first().port
                    val uri = URI("http", null, host, port, "/plots", null, null)
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