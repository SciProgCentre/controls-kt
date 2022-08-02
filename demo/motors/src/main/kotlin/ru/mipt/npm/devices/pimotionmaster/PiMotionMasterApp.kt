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
import ru.mipt.npm.devices.pimotionmaster.PiMotionMasterDevice.Axis.Companion.maxPosition
import ru.mipt.npm.devices.pimotionmaster.PiMotionMasterDevice.Axis.Companion.minPosition
import ru.mipt.npm.devices.pimotionmaster.PiMotionMasterDevice.Axis.Companion.position
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installing
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.fetch
import tornadofx.*

class PiMotionMasterApp : App(PiMotionMasterView::class)

class PiMotionMasterController : Controller() {
    //initialize context
    val context = Context("piMotionMaster"){
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
        with(axis) {
            val min = minPosition.read()
            val max = maxPosition.read()
            val positionProperty = fxProperty(position)
            val startPosition = position.read()
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

    private val connectedProperty: ReadOnlyProperty<Boolean> = device.fxProperty(PiMotionMasterDevice.connected)
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