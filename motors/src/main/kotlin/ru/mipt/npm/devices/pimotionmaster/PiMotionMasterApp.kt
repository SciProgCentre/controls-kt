package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.context.Global
import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.installing
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Parent
import tornadofx.*

class PiMotionMasterApp : App(PiMotionMasterView::class)

class PiMotionMasterController : Controller() {
    //initialize context
    val context = Global.context("piMotionMaster")

    //initialize deviceManager plugin
    val deviceManager: DeviceManager = context.plugins.load(DeviceManager)

    // install device
    val motionMaster: PiMotionMasterDevice by deviceManager.installing(PiMotionMasterDevice)
}

class PiMotionMasterView : View() {

    private val controller: PiMotionMasterController by inject()

    override val root: Parent = borderpane {
        top {
            hbox {
                val host = SimpleStringProperty("127.0.0.1")
                val port = SimpleIntegerProperty(10024)
                fieldset("Address:") {
                    field("Host:") {
                        textfield(host)
                    }
                    field("Port:") {
                        textfield(port)
                    }
                }

                button("Connect") {
                    action {
                        controller.motionMaster.connect(host.get(), port.get())
                    }
                }
            }
        }
    }
}

fun main() {
    launch<PiMotionMasterApp>()
}