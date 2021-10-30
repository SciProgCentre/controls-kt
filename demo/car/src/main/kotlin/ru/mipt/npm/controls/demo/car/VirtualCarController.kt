package ru.mipt.npm.controls.demo.car

import javafx.beans.property.DoubleProperty
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.install
import ru.mipt.npm.controls.demo.car.VirtualCar.Companion.acceleration
import space.kscience.dataforge.context.*
import tornadofx.*

class VirtualCarController : Controller(), ContextAware {

    var device: VirtualCar? = null

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.fetch(DeviceManager)

    fun init() {
        context.launch {
            device = deviceManager.install("virtual-car", VirtualCar)
        }
    }

    fun shutdown() {
        logger.info { "Shutting down..." }
        device?.close()
        logger.info { "Device server stopped" }
        context.close()
    }
}


class VirtualCarControllerView : View(title = " Virtual car controller remote") {
    private val controller: VirtualCarController by inject()
    private var accelerationXProperty: DoubleProperty by singleAssign()
    private var accelerationXTF: TextField by singleAssign()
    private var accelerationYProperty: DoubleProperty by singleAssign()
    private var accelerationYTF: TextField by singleAssign()

    override val root: Parent = vbox {
        hbox {
            label("AccelerationX")
            pane {
                hgrow = Priority.ALWAYS
            }
            accelerationXProperty = doubleProperty()
            accelerationXTF = textfield(accelerationXProperty)
        }
        hbox {
            label("AccelerationY")
            pane {
                hgrow = Priority.ALWAYS
            }
            accelerationYProperty = doubleProperty()
            accelerationYTF = textfield(accelerationYProperty)
        }
        button("Submit") {
            useMaxWidth = true
            action {
                controller.device?.run {
                    launch {
                        acceleration.write(Vector2D(accelerationXProperty.get(),
                            accelerationYProperty.get()))
                    }
                }
            }
        }
    }
}

class VirtualCarControllerApp : App(VirtualCarControllerView::class) {
    private val controller: VirtualCarController by inject()

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
    launch<VirtualCarControllerApp>()
}