package space.kscience.controls.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import space.kscience.controls.api.DescriptionMessage
import space.kscience.controls.client.RemoteDeviceConnect.TestDevice
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.subscribe
import space.kscience.magix.rsocket.rSocketStreamWithWebSockets
import space.kscience.magix.rsocket.rSocketWithWebSockets
import space.kscience.magix.server.startMagixServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MagixLoopTest {

    @Test
    fun realDeviceHub() = runTest(timeout = 5.seconds) {
        val context = Context {
//            coroutineContext(Dispatchers.Default)
            plugin(DeviceManager)
        }

        val server = context.startMagixServer()

        val deviceManager = context.request(DeviceManager)

        val deviceEndpoint = MagixEndpoint.rSocketStreamWithWebSockets("localhost")

        deviceManager.launchMagixService(deviceEndpoint, "device")

        context.launch {
            repeat(10) {
                deviceManager.install("test[$it]", TestDevice)
            }
        }

        val clientEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

        clientEndpoint.subscribe(DeviceManager.magixFormat, originFilter = listOf("device"))
            .map { it.second }
            .filterIsInstance<DescriptionMessage>()
            .onEach { println(it) }
            .launchIn(backgroundScope)


        withContext(Dispatchers.Default) {

            val remoteHub = clientEndpoint.remoteDeviceHub(context, "client", "device")

            assertEquals(0, remoteHub.devices.size)
            clientEndpoint.requestDeviceUpdate("client", "device")


            delay(100)

            assertEquals(10, remoteHub.devices.size)
        }

        clientEndpoint.close()
        deviceEndpoint.close()
        server.stop()
    }
}