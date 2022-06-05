package ru.mipt.npm.devices.pimotionmaster

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.util.InternalAPI
import io.ktor.util.moveToByteArray
import io.ktor.utils.io.writeAvailable
import kotlinx.coroutines.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Global

val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    throwable.printStackTrace()
}

@OptIn(InternalAPI::class)
fun Context.launchPiDebugServer(port: Int, axes: List<String>): Job = launch(exceptionHandler) {
    val virtualDevice = PiMotionMasterVirtualDevice(this@launchPiDebugServer, axes)
    val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind("localhost", port)
    println("Started virtual port server at ${server.localAddress}")

    while (isActive) {
        val socket = server.accept()
        launch(SupervisorJob(coroutineContext[Job])) {
            println("Socket accepted: ${socket.remoteAddress}")
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel()

            val sendJob = launch {
                virtualDevice.receiving().collect {
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
                            virtualDevice.send(array)
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
    runBlocking(Dispatchers.Default) {
        val serverJob = Global.launchPiDebugServer(port, listOf("1", "2"))
        readLine()
        serverJob.cancel()
    }
}