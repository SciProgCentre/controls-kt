package space.kscience.controls.ports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.Global
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// LLM generated code: lifecycle tests for Ktor TCP sockets and cleanup.
class KtorTcpTest {

    @Test
    fun tcpEchoAndStopClosesSocket(): Unit = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var acceptedSocket: Socket? = null
        val echoJob = async(Dispatchers.IO) {
            server.accept().use { socket ->
                acceptedSocket = socket
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = try {
                        input.read(buffer)
                    } catch (exception: SocketException) {
                        if (socket.isClosed) break else throw exception
                    }
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    output.flush()
                }
            }
        }
        var port: KtorTcpPort? = null

        try {
            port = KtorTcpPort.start(Global, "127.0.0.1", server.localPort)
            port.send("test".encodeToByteArray())
            val response = withTimeout(5.seconds) { port.subscribe().first() }
            assertEquals("test", response.decodeToString())

            withTimeout(5.seconds) { port.stop() }
            withTimeout(5.seconds) { echoJob.await() }
        } finally {
            withTimeout(5.seconds) { port?.stop() }
            server.close()
            acceptedSocket?.close()
            echoJob.cancelAndJoin()
        }
    }

    @Test
    fun remoteCloseStopsPort(): Unit = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val acceptAndClose = async(Dispatchers.IO) { server.accept().close() }
        var port: KtorTcpPort? = null

        try {
            port = KtorTcpPort.start(Global, "127.0.0.1", server.localPort)
            withTimeout(5.seconds) { acceptAndClose.await() }
            withTimeout(5.seconds) {
                while (port.lifecycleState != LifecycleState.STOPPED) kotlinx.coroutines.delay(10.milliseconds)
            }
            assertEquals(LifecycleState.STOPPED, port.lifecycleState)
        } finally {
            withTimeout(5.seconds) { port?.stop() }
            server.close()
            acceptAndClose.cancelAndJoin()
        }
    }

    @Test
    fun failedConnectLeavesPortStopped(): Unit = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val portNumber = server.localPort
        server.close()
        val port = KtorTcpPort.build(Global, "127.0.0.1", portNumber)

        assertFailsWith<Exception> { withTimeout(5.seconds) { port.start() } }
        assertEquals(LifecycleState.STOPPED, port.lifecycleState)
        port.stop()
    }
}
