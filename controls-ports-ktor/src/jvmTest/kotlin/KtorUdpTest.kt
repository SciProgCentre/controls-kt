package space.kscience.controls.ports

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.io.Buffer
import kotlin.test.assertEquals

class KtorUdpTest {

    @Test
    fun udp(): Unit = runTest {
        val echoJob = launch {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).udp().bind(port = 8888)

            while (isActive) {
                val datagram = serverSocket.receive()
                val clientAddress = datagram.address
                val response = Buffer { transferFrom(datagram.packet) }
                serverSocket.send(Datagram(response, clientAddress))
            }
        }

        val context = Global
        val port = KtorUdpPort.start(context, "localhost", 8888)

        val response = port.asSynchronousPort().respondStringWithDelimiter("test;", ";")

        assertEquals("test;", response)

        echoJob.cancel()

    }
}