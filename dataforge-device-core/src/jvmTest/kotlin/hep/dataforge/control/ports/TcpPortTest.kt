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
        val socket = try {
            server.accept()
        } catch (ex: Exception) {
            server.close()
            return@launch
        }

        launch {
            println("Socket accepted: ${socket.remoteAddress}")

            try {
                val input = socket.openReadChannel()
                val output = socket.openWriteChannel(autoFlush = true)


                while (isActive) {
                    val line = input.readUTF8Line()

                    //println("${socket.remoteAddress}: $line")
                    output.write("[response] $line")
                }
            } catch (ex: Exception) {
                cancel()
            } finally {
                socket.close()
            }
        }
    }

}

class TcpPortTest {
    @Test
    fun testWithEchoServer() {
        try {
            runBlocking{
                val server = launchEchoServer(22188)
                val port = TcpPort.open("localhost", 22188)

                val logJob = launch {
                    port.incoming().collect {
                        println("Flow: ${it.decodeToString()}")
                    }
                }
                port.startJob.join()
                port.send("aaa\n")
                port.send("ddd\n")

                delay(200)

                cancel()
            }
        } catch (ex: Exception) {
            if (ex !is CancellationException) throw ex
        }
    }

    @Test
    fun testKtorWithEchoServer() {
        try {
            runBlocking{
                val server = launchEchoServer(22188)
                val port = KtorTcpPort.open("localhost", 22188)

                val logJob = launch {
                    port.incoming().collect {
                        println("Flow: ${it.decodeToString()}")
                    }
                }
                port.send("aaa\n")
                port.send("ddd\n")

                delay(200)

                cancel()
            }
        } catch (ex: Exception) {
            if (ex !is CancellationException) throw ex
        }
    }
}