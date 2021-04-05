package space.kscience.dataforge.control.demo

import io.ktor.server.engine.ApplicationEngine
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.launch
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class DemoController : Controller(), ContextAware {

    var device: DemoDevice? = null
    var server: ApplicationEngine? = null
    override val context = Global.context("demoDevice")

    fun init() {
        context.launch {
            val demo = DemoDevice(context)
            device = demo
            server = startDemoDeviceServer(context, demo)
        }
    }

    fun shutdown() {
        logger.info { "Shutting down..." }
        server?.stop(1000, 5000)
        logger.info { "Visualization server stopped" }
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
                    timeScaleValue = timeScaleSlider.value
                    sinScaleValue = xScaleSlider.value
                    cosScaleValue = yScaleSlider.value
                }
            }
        }
        button("Show plots") {
            useMaxWidth = true
            action {
                controller.server?.run {
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