package ru.mipt.npm.controls.demo.car

import io.ktor.server.engine.ApplicationEngine
import javafx.beans.property.DoubleProperty
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import javafx.stage.Stage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.client.connectToMagix
import ru.mipt.npm.controls.demo.car.IVirtualCar.Companion.acceleration
import ru.mipt.npm.controls.manager.DeviceManager
import ru.mipt.npm.controls.manager.install
import ru.mipt.npm.controls.storage.storeMessages
import ru.mipt.npm.controls.xodus.XodusDeviceMessageStorage
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithTcp
import ru.mipt.npm.magix.server.startMagixServer
import ru.mipt.npm.magix.storage.xodus.storeInXodus
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import tornadofx.*
import java.nio.file.Paths

class VirtualCarController : Controller(), ContextAware {

    var virtualCar: VirtualCar? = null
    var magixVirtualCar: MagixVirtualCar? = null
    var magixServer: ApplicationEngine? = null
    var xodusStorageJob: Job? = null
    var storageEndpoint: MagixEndpoint? = null
    //var mongoStorageJob: Job? = null

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.fetch(DeviceManager, Meta {
        "xodusConfig" put {
            "entityStorePath" put deviceEntityStorePath.toString()
        }
    })

    fun init() {
        context.launch {
            virtualCar = deviceManager.install("virtual-car", VirtualCar)

            //starting magix event loop and connect it to entity store
            magixServer = startMagixServer(enableRawRSocket = true, enableZmq = true)

            storageEndpoint = MagixEndpoint.rSocketWithTcp("localhost").apply {
                storeInXodus(this@launch, magixEntityStorePath)
            }

            magixVirtualCar = deviceManager.install("magix-virtual-car", MagixVirtualCar)
            //connect to device entity store
            xodusStorageJob = deviceManager.storeMessages(XodusDeviceMessageStorage)
            //Create mongo client and connect to MongoDB
            //mongoStorageJob = deviceManager.storeMessages(DefaultAsynchronousMongoClientFactory)
            //Launch device client and connect it to the server
            val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost")
            deviceManager.connectToMagix(deviceEndpoint)
        }
    }

    fun shutdown() {
        logger.info { "Shutting down..." }
        magixServer?.stop(1000, 5000)
        logger.info { "Magix server stopped" }
        magixVirtualCar?.close()
        logger.info { "Magix virtual car server stopped" }
        virtualCar?.close()
        logger.info { "Virtual car server stopped" }
        context.close()
    }

    companion object {
        val deviceEntityStorePath = Paths.get(".messages")
        val magixEntityStorePath = Paths.get(".server_messages")
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
                controller.virtualCar?.run {
                    launch {
                        acceleration.write(
                            Vector2D(
                                accelerationXProperty.get(),
                                accelerationYProperty.get()
                            )
                        )
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