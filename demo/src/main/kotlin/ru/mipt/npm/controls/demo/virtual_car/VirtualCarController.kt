package ru.mipt.npm.controls.demo.virtual_car

import io.ktor.server.engine.ApplicationEngine
import javafx.beans.property.DoubleProperty
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.launch
import org.eclipse.milo.opcua.sdk.server.OpcUaServer
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.client.connectToMagix
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.install
import ru.mipt.npm.controls.demo.virtual_car.VirtualCar.Companion.acceleration
import ru.mipt.npm.controls.opcua.server.OpcUaServer
import ru.mipt.npm.controls.opcua.server.endpoint
import ru.mipt.npm.controls.opcua.server.serveDevices
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithTcp
import ru.mipt.npm.magix.server.startMagixServer
import space.kscience.dataforge.context.*
import tornadofx.*

class VirtualCarController : Controller(), ContextAware {

    var device: VirtualCar? = null
    var magixServer: ApplicationEngine? = null
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
            device = deviceManager.install("virtual-car", VirtualCar)
            //starting magix event loop
            magixServer = startMagixServer(enableRawRSocket = true, enableZmq = true)
            //Launch device client and connect it to the server
            val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost", DeviceMessage.serializer())
            deviceManager.connectToMagix(deviceEndpoint)

            opcUaServer.startup()
            opcUaServer.serveDevices(deviceManager)
        }
    }

    fun shutdown() {
        logger.info { "Shutting down..." }
        opcUaServer.shutdown()
        logger.info { "OpcUa server stopped" }
        magixServer?.stop(1000, 5000)
        logger.info { "Magix server stopped" }
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
                        acceleration.write(Coordinates(accelerationXProperty.get(), accelerationYProperty.get()))
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