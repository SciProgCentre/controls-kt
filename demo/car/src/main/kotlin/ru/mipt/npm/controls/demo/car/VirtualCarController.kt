package ru.mipt.npm.controls.demo.car

import io.ktor.server.engine.*
import javafx.beans.property.DoubleProperty
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import javafx.stage.Stage
import jetbrains.exodus.entitystore.PersistentEntityStore
import jetbrains.exodus.entitystore.PersistentEntityStoreImpl
import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.client.connectToMagix
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.install
import ru.mipt.npm.controls.demo.car.IVirtualCar.Companion.acceleration
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.rsocket.rSocketWithTcp
import ru.mipt.npm.magix.server.startMagixServer
import space.kscience.dataforge.context.*
import tornadofx.*

class VirtualCarController : Controller(), ContextAware {

    var virtualCar: VirtualCar? = null
    var magixVirtualCar: MagixVirtualCar? = null
    var magixServer: ApplicationEngine? = null
    var entityStore: PersistentEntityStore? = null

    override val context = Context("demoDevice") {
        plugin(DeviceManager)
    }

    private val deviceManager = context.fetch(DeviceManager)

    fun init() {
        context.launch {
            virtualCar = deviceManager.install("virtual-car", VirtualCar)
            //starting magix event loop
            magixServer = startMagixServer(enableRawRSocket = true, enableZmq = true)
            magixVirtualCar = deviceManager.install("magix-virtual-car", MagixVirtualCar)
            entityStore = PersistentEntityStores.newInstance("/home/marvel1337/2021/SCADA/.messages")
            //Launch device client and connect it to the server
            val deviceEndpoint = MagixEndpoint.rSocketWithTcp("localhost", DeviceMessage.serializer())
            if (entityStore != null) {
                deviceManager.connectToMagix(deviceEndpoint, entityStore = entityStore as PersistentEntityStoreImpl)
            } else {
                deviceManager.connectToMagix(deviceEndpoint)
            }
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
        entityStore?.close()
        logger.info { "Entity store closed" }
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
                controller.virtualCar?.run {
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