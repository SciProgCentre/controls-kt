package space.kscience.controls.ports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.Global
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// LLM generated code: lifecycle tests for Ktor UDP sockets and cleanup.
class KtorUdpTest {

    @Test
    fun udpEchoAndStopReleasesLocalPort(): Unit = runBlocking {
        val loopback = InetAddress.getLoopbackAddress()
        val server = DatagramSocket(0, loopback)
        val echoJob = launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val packet = DatagramPacket(ByteArray(1024), 1024)
                    server.receive(packet)
                    server.send(DatagramPacket(packet.data, packet.length, packet.address, packet.port))
                }
            } catch (exception: SocketException) {
                if (isActive) throw exception
            }
        }
        val localPort = DatagramSocket(0, loopback).use { it.localPort }
        var port: KtorUdpPort? = null

        try {
            port = KtorUdpPort.start(
                Global,
                "127.0.0.1",
                server.localPort,
                localPort = localPort,
                localHost = "127.0.0.1",
            )

            port.send("test".encodeToByteArray())
            val response = withTimeout(5.seconds) { port.subscribe().first() }
            assertEquals("test", response.decodeToString())

            port.stop()
            delay(1.seconds)
            DatagramSocket(localPort, loopback).use { assertEquals(localPort, it.localPort) }
        } finally {
            port?.stop()
            echoJob.cancel()
            server.close()
            echoJob.cancelAndJoin()
        }
    }

    @Test
    fun failedOpenReleasesSelectorAndAllowsRetry(): Unit = runBlocking {
        val loopback = InetAddress.getLoopbackAddress()
        val remote = DatagramSocket(0, loopback)
        val occupied = DatagramSocket(0, loopback)
        val localPort = occupied.localPort

        try {
            val failedPort = KtorUdpPort.build(
                Global,
                "127.0.0.1",
                remote.localPort,
                localPort = localPort,
                localHost = "127.0.0.1",
            )
            assertFailsWith<Exception> { withTimeout(5.seconds) { failedPort.start() } }
            assertEquals(LifecycleState.STOPPED, failedPort.lifecycleState)

            occupied.close()
            val retryPort = KtorUdpPort.start(
                Global,
                "127.0.0.1",
                remote.localPort,
                localPort = localPort,
                localHost = "127.0.0.1",
            )
            retryPort.stop()
        } finally {
            occupied.close()
            remote.close()
        }
    }
}
