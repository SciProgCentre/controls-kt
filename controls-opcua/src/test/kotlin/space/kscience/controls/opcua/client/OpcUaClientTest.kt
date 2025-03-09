package space.kscience.controls.opcua.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.junit.jupiter.api.Test
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doubleProperty
import space.kscience.controls.spec.read
import space.kscience.dataforge.meta.MetaConverter
import kotlin.test.Ignore

class OpcUaClientTest {
    class DemoOpcUaDevice(config: MiloConfiguration) : OpcUaDeviceBySpec<DemoOpcUaDevice>(DemoOpcUaDevice, config) {

        //val randomDouble by opcDouble(NodeId(2, "Dynamic/RandomDouble"))

        suspend fun readRandomDouble() = readOpc(NodeId(2, "Dynamic/RandomDouble"), MetaConverter.double)


        companion object : DeviceSpec<DemoOpcUaDevice>() {
            /**
             * Build a device. This is not a part of the specification
             */
            fun build(): DemoOpcUaDevice {
                val config = MiloConfiguration {
                    endpointUrl = "opc.tcp://milo.digitalpetri.com:62541/milo"
                }
                return DemoOpcUaDevice(config)
            }

            val randomDouble by doubleProperty { readRandomDouble() }

        }

    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Ignore
    fun testReadDouble() = runTest {
        val device = DemoOpcUaDevice.build()
        device.start()
        println(device.read(DemoOpcUaDevice.randomDouble))
        device.stop()
    }

}