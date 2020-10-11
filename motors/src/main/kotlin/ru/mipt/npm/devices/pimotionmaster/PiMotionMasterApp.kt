package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.context.Global
import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.installing
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.Parent
import javafx.scene.layout.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    val device = controller.motionMaster

    private val connectedProperty: ReadOnlyProperty<Boolean> = device.connected.fxProperty(device)
    private val debugServerJobProperty = SimpleObjectProperty<Job>()
    private val debugServerStarted = debugServerJobProperty.booleanBinding { it != null }
    private val axisList = FXCollections.observableArrayList<Map.Entry<String, PiMotionMasterDevice.Axis>>()

    override val root: Parent = borderpane {
        top {
            form {
                val host = SimpleStringProperty("127.0.0.1")
                val port = SimpleIntegerProperty(10024)
                fieldset("Address:") {
                    field("Host:") {
                        textfield(host) {
                            enableWhen(debugServerStarted.not())
                        }
                    }
                    field("Port:") {
                        textfield(port){
                            stripNonNumeric()
                        }
                        button {
                            hgrow = Priority.ALWAYS
                            textProperty().bind(debugServerStarted.stringBinding {
                                if (it != true) {
                                    "Start debug server"
                                } else {
                                    "Stop debug server"
                                }
                            })
                            action {
                                if (!debugServerStarted.get()) {
                                    debugServerJobProperty.value =
                                        controller.context.launchPiDebugServer(port.get(), listOf("1", "2"))
                                } else {
                                    debugServerJobProperty.get().cancel()
                                    debugServerJobProperty.value = null
                                }
                            }
                        }
                    }
                }

                button {
                    hgrow = Priority.ALWAYS
                    textProperty().bind(connectedProperty.stringBinding {
                        if (it == false) {
                            "Connect"
                        } else {
                            "Disconnect"
                        }
                    })
                    action {
                        if (!connectedProperty.value) {
                            device.connect(host.get(), port.get())
                            axisList.addAll(device.axes.entries)
                        } else {
                            axisList.removeAll()
                            device.disconnect()
                        }
                    }
                }


            }
        }

        center {
            listview(axisList) {
                cellFormat { (name, axis) ->
                    hbox {
                        minHeight = 40.0
                        label(name)
                        controller.context.launch {
                            val min = axis.minPosition.readTyped(true)
                            val max = axis.maxPosition.readTyped(true)
                            runLater {
                                slider(min.toDouble()..max.toDouble()){
                                    hgrow = Priority.ALWAYS
                                    valueProperty().onChange {
                                        isDisable = true
                                        launch {
                                            axis.move(value)
                                            runLater {
                                                isDisable = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun main() {
    launch<PiMotionMasterApp>()
}