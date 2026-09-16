package space.kscience.controls.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import space.kscience.controls.api.DescriptionMessage
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
import space.kscience.dataforge.names.Name
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.subscribe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VirtualMagixEndpoint(val tree: DeviceTree, val scope: CoroutineScope) : MagixEndpoint {

    private val messages = MutableSharedFlow<DeviceMessage>(10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        tree.messageFlow().onEach {
            messages.emit(it)
        }.launchIn(scope)
    }

    override fun subscribe(
        filter: MagixMessageFilter,
    ): Flow<MagixMessage> = messages.map {
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

        val virtualMagixEndpoint = VirtualMagixEndpoint(deviceManager, backgroundScope)

        val remoteDevice: DeviceClient = virtualMagixEndpoint.remoteDevice(context, "client", "device", Name.of("test"))

        assertContains(0.0..1.0, remoteDevice.read(TestDevice.value))

    }

    @Test
    fun deviceHub() = runTest(timeout = 5.seconds) {
        val context = Context {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)

        val virtualMagixEndpoint = VirtualMagixEndpoint(deviceManager, backgroundScope)

        val remoteHub = virtualMagixEndpoint.remoteDeviceTree(context, "client", "device")

        assertEquals(0, remoteHub.children.size)

        launch {
            repeat(10) {
                deviceManager.installTree("test[$it]", TestDevice)
            }
        }

        launch {
            virtualMagixEndpoint.subscribe(DeviceManager.magixFormat, originFilter = listOf("device"))
                .map { it.second }
                .filterIsInstance<DescriptionMessage>()
                .onEach { println(it) }
                .take(10)
                .collect()

            assertEquals(10, remoteHub.children.size)
        }

        virtualMagixEndpoint.requestDeviceUpdate("client", "device")


    }
}