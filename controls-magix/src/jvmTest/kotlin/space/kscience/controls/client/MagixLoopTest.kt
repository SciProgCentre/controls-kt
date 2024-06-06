package space.kscience.controls.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.client.RemoteDeviceConnect.TestDevice
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.startMagixServer
import kotlin.test.Test
import kotlin.test.assertEquals

class MagixLoopTest {

    @Test
    fun realDeviceHub() = runTest {
        val context = Context {
            coroutineContext(Dispatchers.Default)
            plugin(DeviceManager)
        }

        val server = context.startMagixServer()

        val deviceManager = context.request(DeviceManager)

        val deviceEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

        deviceManager.launchMagixService(deviceEndpoint, "device")

        val trigger = CompletableDeferred<Unit>()

        context.launch {
            repeat(10) {
                deviceManager.install("test[$it]", TestDevice)
            }
            delay(100)
            trigger.complete(Unit)
        }

        val clientEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

        val remoteHub = clientEndpoint.remoteDeviceHub(context, "client", "device")

        assertEquals(0, remoteHub.devices.size)
        clientEndpoint.requestDeviceUpdate("client", "device")
        trigger.join()
        assertEquals(10, remoteHub.devices.size)
        server.stop()
    }
}