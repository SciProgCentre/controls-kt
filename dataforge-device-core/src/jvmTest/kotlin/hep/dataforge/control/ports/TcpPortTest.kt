package hep.dataforge.control.ports

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.cio.write
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

@OptIn(KtorExperimentalAPI::class)
fun CoroutineScope.launchEchoServer(port: Int): Job = launch {
    val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind(InetSocketAddress("localhost", port))
    println("Started echo telnet server at ${server.localAddress}")

    while (isActive) {
        val socket = server.accept()

        launch {
            println("Socket accepted: ${socket.remoteAddress}")

            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = true)

            try {
                while (true) {
                    val line = input.readUTF8Line()

                    println("${socket.remoteAddress}: $line")
                    output.write("$line\r\n")
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
                socket.close()
            }
        }
    }

}

class TcpPortTest {
    @Test
    fun testWithEchoServer() {
        runBlocking {
            coroutineScope {
                val server = launchEchoServer(22188)
                val port = openTcpPort("localhost", 22188)
                launch {
                    port.flow().collect {
                        println("Flow: ${it.decodeToString()}")
                    }
                }
                delay(100)
                port.send("aaa\n")
                delay(10)
                port.send("ddd\n")

                delay(200)
                cancel()
            }
//            port.close()
//            server.cancel()
        }


    }
}