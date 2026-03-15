package space.kscience.controls.opcua.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
import org.junit.jupiter.api.Test
import space.kscience.controls.api.Device
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.opcua.server.OpcUaServer
import space.kscience.controls.opcua.server.endpoint
import space.kscience.controls.opcua.server.serveDevices
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.MetaConverter
import kotlin.random.Random
import kotlin.test.assertEquals

class OpcUaClientTest {

    interface TestDevice : Device {

        suspend fun readRandomDouble(): Double

        suspend fun writeRandomDouble(value: Double)

        companion object : DeviceSpec<TestDevice>() {

            val randomDouble by mutableDoubleProperty(
                read = { readRandomDouble() }, write = { _, value -> writeRandomDouble(value) })
        }
    }

    class TestDeviceImpl(context: Context) : TestDevice, DeviceBySpec<TestDevice>(TestDevice, context) {
        private var value: Double = 0.0

        override suspend fun readRandomDouble(): Double = value
        override suspend fun writeRandomDouble(value: Double) {
            this.value = value
        }
    }

    class DemoOpcUaDevice(config: MiloConfiguration) : TestDevice, OpcUaDeviceBySpec<TestDevice>(TestDevice, config) {

        //val randomDouble by opcDouble(NodeId(2, "Dynamic/RandomDouble"))

        override suspend fun readRandomDouble() = readOpc(
            nodeId = NodeId(2, "root/randomDouble"),
            converter = MetaConverter.double
        )

        override suspend fun writeRandomDouble(value: Double): Unit {
            client.writeValues(listOf(NodeId(2, "root/randomDouble")), listOf(DataValue(Variant(value))))
        }

        companion object : DeviceSpec<DemoOpcUaDevice>() {
            /**
             * Build a device. This is not a part of the specification
             */
            fun build(): DemoOpcUaDevice {
                val config = MiloConfiguration {
                    endpointUrl = "opc.tcp://localhost:9091"
                }
                return DemoOpcUaDevice(config)
            }

            val randomDouble by mutableDoubleProperty(
                read = { readRandomDouble() }, write = { _, value -> writeRandomDouble(value) })

        }

    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testReadDouble() = runTest {
        val context = Context {
            plugin(DeviceManager)
        }

        val backDevice = context.install("root",TestDeviceImpl(context))

        val randomDouble = Random.nextDouble()
//
//        backDevice.write(TestDevice.randomDouble, randomDouble)

        val server = OpcUaServer {
            setApplicationName(LocalizedText.english("center.sciprog.controls.demo"))

            endpoint {
                setBindPort(9091)
            }

        }.apply {
            serveDevices(context.request(DeviceManager))
            startup().await()
        }


        val device = DemoOpcUaDevice.build()
        device.start()


        device.write(DemoOpcUaDevice.randomDouble, randomDouble)

        val res = device.read(DemoOpcUaDevice.randomDouble)



        assertEquals(randomDouble, res)
        device.stop()

        context.close()
    }

}