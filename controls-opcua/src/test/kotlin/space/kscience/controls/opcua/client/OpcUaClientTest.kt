package space.kscience.controls.opcua.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.junit.jupiter.api.Test
import space.kscience.controls.opcua.client.OpcUaClientTest.DemoMiloDevice.Companion.randomDouble
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.doubleProperty
import space.kscience.dataforge.meta.transformations.MetaConverter

class OpcUaClientTest {
    class DemoMiloDevice(config: MiloConfiguration) : MiloDeviceBySpec<DemoMiloDevice>(DemoMiloDevice, config) {

        //val randomDouble by opcDouble(NodeId(2, "Dynamic/RandomDouble"))

        suspend fun readRandomDouble() = readOpc(NodeId(2, "Dynamic/RandomDouble"), MetaConverter.double)


        companion object : DeviceSpec<DemoMiloDevice>() {
            fun build(): DemoMiloDevice {
                val config = MiloConfiguration {
                    endpointUrl = "opc.tcp://milo.digitalpetri.com:62541/milo"
//                    username = MiloUsername{
//                        username = "user1"
//                        password = "password"
//                    }
                }
                return DemoMiloDevice(config)
            }

            inline fun <R> use(block: DemoMiloDevice.() -> R): R = build().use(block)

            val randomDouble by doubleProperty(read = DemoMiloDevice::readRandomDouble)

        }

    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testReadDouble() = runTest {
        println(DemoMiloDevice.use { randomDouble.read() })
    }

}