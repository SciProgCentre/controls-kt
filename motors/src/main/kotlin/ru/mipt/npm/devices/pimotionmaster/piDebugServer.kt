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
import io.ktor.utils.io.writeAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.net.InetSocketAddress

val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    throwable.printStackTrace()
}

@OptIn(KtorExperimentalAPI::class, InternalAPI::class)
fun CoroutineScope.launchPiDebugServer(port: Int, virtualPort: Port): Job = launch(exceptionHandler) {
    val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind(InetSocketAddress("localhost", port))
    println("Started virtual port server at ${server.localAddress}")

    while (isActive) {
        val socket = server.accept()
        launch(SupervisorJob(coroutineContext[Job])) {
            println("Socket accepted: ${socket.remoteAddress}")
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel()

            val sendJob = launch {
                virtualPort.receiving().collect {
                    //println("Sending: ${it.decodeToString()}")
                    output.writeAvailable(it)
                    output.flush()
                }
            }

            try {
                while (isActive) {
                    input.read { buffer ->
                        val array = buffer.moveToByteArray()
                        launch {
                            virtualPort.send(array)
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                sendJob.cancel()
                socket.close()
            } finally {
                println("Socket closed")
            }

        }
    }
}

fun main() {
    val port = 10024
    val virtualDevice = PiMotionMasterVirtualDevice(Global, listOf("1", "2"))
    val virtualPort = VirtualPort(virtualDevice, Global)
    runBlocking(Dispatchers.Default) {
        val serverJob = launchPiDebugServer(port, virtualPort)
        readLine()
        serverJob.cancel()
    }
}