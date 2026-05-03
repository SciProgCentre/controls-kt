package space.kscience.controls.opcua.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.junit.jupiter.api.Test
import space.kscience.controls.spec.doubleProperty
import space.kscience.controls.spec.read
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.meta.MetaConverter
import kotlin.test.Ignore

class OpcUaClientTest {
    object DemoOpcUaDevice : OpcUaDeviceFactory() {

        val randomDouble by doubleProperty {
            readOpc(NodeId(2, "Dynamic/RandomDouble"), MetaConverter.double)
        }

    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Ignore
    fun testReadDouble() = runTest {
        val config = MiloConfiguration {
            endpointUrl = "opc.tcp://milo.digitalpetri.com:62541/milo"
        }
        val device = DemoOpcUaDevice.build(Global, config.meta)
        device.start()
        println(device.read(DemoOpcUaDevice.randomDouble))
        device.stop()
    }

}