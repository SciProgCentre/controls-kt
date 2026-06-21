package space.kscience.controls.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installTree
import space.kscience.controls.manager.messageFlow
import space.kscience.controls.manager.respondMessage
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.names.asName
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class VirtualMagixEndpoint(val tree: DeviceTree) : MagixEndpoint {

    private val messages = MutableSharedFlow<DeviceMessage>(1)

    override fun subscribe(
        filter: MagixMessageFilter,
    ): Flow<MagixMessage> = merge(tree.messageFlow(), messages).map {
        MagixMessage(
            format = DeviceManager.magixFormat.defaultFormat,
            payload = MagixEndpoint.magixJson.encodeToJsonElement(DeviceManager.magixFormat.serializer, it),
            sourceEndpoint = "device",
        )
    }

    override suspend fun broadcast(message: MagixMessage) {
        tree.respondMessage(
            Json.decodeFromJsonElement(DeviceManager.magixFormat.serializer, message.payload)
        ).forEach {
            messages.emit(it)
        }
    }

    override fun close() {
        //
    }
}


internal class RemoteDeviceConnect {

    object TestDevice : DeviceWithStateFactory<Random>() {

        context(device: DeviceBase)
        override suspend fun createState(): Random {
            device.doRecurring((device.meta["delay"].int ?: 10).milliseconds) {
                device.read(value)
            }
            return Random(device.meta["seed"].int ?: 0)
        }

        val value by doubleProperty { nextDouble() }
    }

    @Test
    fun deviceClient() = runTest {
        val context = Context {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)

        deviceManager.installTree("test", TestDevice)

        val virtualMagixEndpoint = VirtualMagixEndpoint(deviceManager)

        val remoteDevice: DeviceClient = virtualMagixEndpoint.remoteDevice(context, "client", "device", "test".asName())

        assertContains(0.0..1.0, remoteDevice.read(TestDevice.value))

    }

    @Test
    fun deviceHub() = runTest {
        val context = Context {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)

        launch {
            delay(50.milliseconds)
            repeat(10) {
                deviceManager.installTree("test[$it]", TestDevice)
            }
        }

        val virtualMagixEndpoint = VirtualMagixEndpoint(deviceManager)

        val remoteHub = virtualMagixEndpoint.remoteDeviceHub(context, "client", "device")

        assertEquals(0, remoteHub.children.size)

        delay(60.milliseconds)
        //switch context to use actual delay
        withContext(Dispatchers.Default) {
            virtualMagixEndpoint.requestDeviceUpdate("client", "device")
            delay(30.milliseconds)
            assertEquals(10, remoteHub.children.size)
        }
    }
}