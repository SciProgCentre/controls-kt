package ru.mipt.npm.devices.pimotionmaster

import hep.dataforge.context.Global
import hep.dataforge.control.ports.Port
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.moveToByteArray
import io.ktor.utils.io.readUntilDelimiter
import io.ktor.utils.io.writeAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.net.InetSocketAddress
import java.nio.ByteBuffer

private val delimeter = ByteBuffer.wrap("\n".encodeToByteArray())

@OptIn(KtorExperimentalAPI::class, InternalAPI::class)
fun CoroutineScope.launchPiDebugServer(port: Int, virtualPort: Port): Job = launch {
    val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind(InetSocketAddress("localhost", port))
    println("Started virtual port server at ${server.localAddress}")

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

                val buffer = ByteBuffer.allocate(1024)
                launch {
                    virtualPort.receiving().collect {
                        println("Sending: ${it.decodeToString()}")
                        output.writeAvailable(it)
                    }
                }
                while (isActive) {
                    buffer.rewind()
                    val read = input.readUntilDelimiter(delimeter, buffer)
                    if (read > 0) {
                        buffer.flip()
                        val array = buffer.moveToByteArray()
                        println("Received: ${array.decodeToString()}")
                        virtualPort.send(array)
                    }
                }
            } catch (ex: Exception) {
                cancel()
            } finally {
                socket.close()
            }
        }
    }
}

fun main() {
    val port = 10024
    val virtualPort = PiMotionMasterVirtualPort(Global)
    runBlocking(Dispatchers.Default) {
        val serverJob = launchPiDebugServer(port, virtualPort)
        readLine()
        serverJob.cancel()
    }
}