package space.kscience.controls.client

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DescriptionMessage
import space.kscience.controls.client.RemoteDeviceConnect.TestDevice
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installTree
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
    fun realDeviceHub(): Unit = runTest(timeout = 5.seconds) {
        val context = Context {
            plugin(DeviceManager)
        }

        val server = context.startMagixServer()

        val deviceManager = context.request(DeviceManager)

        val deviceEndpoint = MagixEndpoint.rSocketStreamWithWebSockets("localhost")

        deviceManager.launchMagixService(deviceEndpoint, "device")

        val clientEndpoint = MagixEndpoint.rSocketWithWebSockets("localhost")

        val remoteHub = clientEndpoint.remoteDeviceTree(context, "client", "device")

        assertEquals(0, remoteHub.children.size)

        launch {
            repeat(10) {
                deviceManager.installTree("test[$it]", TestDevice)
            }
        }

        launch {
            clientEndpoint.subscribe(DeviceManager.magixFormat, originFilter = listOf("device"))
                .map { it.second }
                .filterIsInstance<DescriptionMessage>()
                .take(10)
                .onEach { println(it) }
                .collect()

            assertEquals(10, remoteHub.children.size)
        }

        clientEndpoint.requestDeviceUpdate("client", "device")



        clientEndpoint.close()
        deviceEndpoint.close()
        server.stop()
    }
}