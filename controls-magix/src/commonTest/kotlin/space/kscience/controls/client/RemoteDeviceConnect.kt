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
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.hubMessageFlow
import space.kscience.controls.manager.install
import space.kscience.controls.manager.respondHubMessage
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

class VirtualMagixEndpoint(val hub: DeviceHub) : MagixEndpoint {

    private val additionalMessages = MutableSharedFlow<DeviceMessage>(1)

    override fun subscribe(
        filter: MagixMessageFilter,
    ): Flow<MagixMessage> = merge(hub.hubMessageFlow(), additionalMessages).map {
        MagixMessage(
            format = DeviceManager.magixFormat.defaultFormat,
            payload = MagixEndpoint.magixJson.encodeToJsonElement(DeviceManager.magixFormat.serializer, it),
            sourceEndpoint = "device",
        )
    }

    override suspend fun broadcast(message: MagixMessage) {
        hub.respondHubMessage(
            Json.decodeFromJsonElement(DeviceManager.magixFormat.serializer, message.payload)
        ).forEach {
            additionalMessages.emit(it)
        }
    }

    override fun close() {
        //
    }
}


internal class RemoteDeviceConnect {

    object TestDevice : DeviceFactory<Random>() {

        override suspend fun DeviceBase.createState(): Random {
            doRecurring((meta["delay"].int ?: 10).milliseconds) {
                read(value)
            }
            return Random(meta["seed"].int ?: 0)
        }

        val value by doubleProperty { nextDouble() }
    }

    @Test
    fun deviceClient() = runTest {
        val context = Context {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)

        deviceManager.install("test", TestDevice)

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
                deviceManager.install("test[$it]", TestDevice)
            }
        }

        val virtualMagixEndpoint = VirtualMagixEndpoint(deviceManager)

        val remoteHub = virtualMagixEndpoint.remoteDeviceHub(context, "client", "device")

        assertEquals(0, remoteHub.devices.size)

        delay(60)
        //switch context to use actual delay
        withContext(Dispatchers.Default) {
            virtualMagixEndpoint.requestDeviceUpdate("client", "device")
            delay(30.milliseconds)
            assertEquals(10, remoteHub.devices.size)
        }
    }
}