package space.kscience.controls.constructor

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.spec.doRecurring
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class DeviceGroupTest {

    class TestDevice(context: Context) : DeviceConstructor(context) {

        companion object : Factory<Device> {
            override fun build(context: Context, meta: Meta): Device = TestDevice(context)
        }
    }

    @Test
    @Ignore
    fun testRecurringRead() = runTest {
        var counter = 10
        val testDevice = Global.request(DeviceManager).install("test", TestDevice)
        testDevice.doRecurring(1.milliseconds) {
            counter--
            println(counter)
            if (counter <= 0) {
                testDevice.stop()
            }
            error("Error!")
        }
        testDevice.messageFlow.first { it is DeviceLifeCycleMessage && it.state == LifecycleState.STOPPED }
        println("stopped")
    }
}