package ru.mipt.npm.devices.pimotionmaster

import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.context.fetch
import space.kscience.dataforge.control.controllers.DeviceManager
import space.kscience.dataforge.control.controllers.installing
import tornadofx.*

class PiMotionMasterApp : App(PiMotionMasterView::class)

class PiMotionMasterController : Controller() {
    //initialize context
    val context = Global.buildContext("piMotionMaster"){
        plugin(DeviceManager)
    }

    //initialize deviceManager plugin
    val deviceManager: DeviceManager = context.fetch(DeviceManager)

    // install device
    val motionMaster: PiMotionMasterDevice by deviceManager.installing(PiMotionMasterDevice)
}

fun VBox.piMotionMasterAxis(
    axisName: String,
    axis: PiMotionMasterDevice.Axis,
    coroutineScope: CoroutineScope,
) = hbox {
    alignment = Pos.CENTER
    label(axisName)
    coroutineScope.launch {
        val min = axis.minPosition.readTyped(true)
        val max = axis.maxPosition.readTyped(true)
        val positionProperty = axis.position.fxProperty(axis)
        val startPosition = axis.position.readTyped(true)
        runLater {
            vbox {
                hgrow = Priority.ALWAYS
                slider(min..max, startPosition) {
                    minWidth = 300.0
                    isShowTickLabels = true
                    isShowTickMarks = true
                    minorTickCount = 10
                    majorTickUnit = 1.0
                    valueProperty().onChange {
                        coroutineScope.launch {
                            axis.move(value)
                        }
                    }
                }
                slider(min..max) {
                    isDisable = true
                    valueProperty().bind(positionProperty)
                }
            }
        }
    }
}

fun Parent.axisPane(axes: Map<String, PiMotionMasterDevice.Axis>, coroutineScope: CoroutineScope) {
    vbox {
        axes.forEach { (name, axis) ->
            this.piMotionMasterAxis(name, axis, coroutineScope)
        }
    }
}


class PiMotionMasterView : View() {

    private val controller: PiMotionMasterController by inject()
    val device = controller.motionMaster

    private val connectedProperty: ReadOnlyProperty<Boolean> = device.connected.fxProperty(device)
    private val debugServerJobProperty = SimpleObjectProperty<Job>()
    private val debugServerStarted = debugServerJobProperty.booleanBinding { it != null }
    //private val axisList = FXCollections.observableArrayList<Map.Entry<String, PiMotionMasterDevice.Axis>>()

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
                        textfield(port) {
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
                                        controller.context.launchPiDebugServer(port.get(), listOf("1", "2", "3", "4"))
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
                            center {
                                axisPane(device.axes,controller.context)
                            }
                        } else {
                            this@borderpane.center = null
                            device.disconnect()
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