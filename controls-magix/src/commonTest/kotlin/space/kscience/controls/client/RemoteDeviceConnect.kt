package space.kscience.controls.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.manager.respondMessage
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.names.Name
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.time.Duration.Companion.milliseconds


internal class RemoteDeviceConnect {

    class TestDevice(context: Context, meta: Meta) : DeviceBySpec<TestDevice>(TestDevice, context, meta) {
        private val rng = Random(meta["seed"].int ?: 0)

        private val randomValue get() = rng.nextDouble()

        companion object : DeviceSpec<TestDevice>(), Factory<TestDevice> {

            override fun build(context: Context, meta: Meta): TestDevice = TestDevice(context, meta)

            val value by doubleProperty { randomValue }

            override suspend fun TestDevice.onOpen() {
                doRecurring((meta["delay"].int ?: 10).milliseconds) {
                    read(value)
                }
            }
        }
    }

    @Test
    fun wrapper() = runTest {
        val context = Context {
            plugin(DeviceManager)
        }

        val device = context.request(DeviceManager).install("test", TestDevice)

        val virtualMagixEndpoint = object : MagixEndpoint {


            override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = device.messageFlow.map {
                MagixMessage(
                    format = DeviceManager.magixFormat.defaultFormat,
                    payload = MagixEndpoint.magixJson.encodeToJsonElement(DeviceManager.magixFormat.serializer, it),
                    sourceEndpoint = "source",
                )
            }

            override suspend fun broadcast(message: MagixMessage) {
                device.respondMessage(
                    Name.EMPTY,
                    Json.decodeFromJsonElement(DeviceManager.magixFormat.serializer, message.payload)
                )
            }

            override fun close() {
                //
            }
        }

        val remoteDevice = virtualMagixEndpoint.remoteDevice(context, "source", "target", Name.EMPTY)

        assertContains(0.0..1.0, remoteDevice.read(TestDevice.value))
    }
}