package center.sciprog.controls.demo.thermo

import kotlinx.coroutines.coroutineScope
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.vision.showDashboard
import space.kscience.dataforge.context.Context
import space.kscience.visionforge.VisionManager


suspend fun main(): Unit = coroutineScope{

    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
        plugin(VisionManager)
    }

    val thermoHub = context.setup()


    context.showDashboard {

    }

}