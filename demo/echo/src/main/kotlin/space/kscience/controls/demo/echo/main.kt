package space.kscience.controls.demo.echo

import io.ktor.server.application.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import ru.mipt.npm.magix.rsocket.rSocketStreamWithWebSockets
import ru.mipt.npm.magix.server.startMagixServer
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private suspend fun MagixEndpoint.collectEcho(scope: CoroutineScope, n: Int) {
    val complete = CompletableDeferred<Boolean>()

    val responseIds = HashSet<String>()

    scope.launch {
        subscribe(
            MagixMessageFilter(
                origin = listOf("loop")
            )
        ).collect { message ->
            if (message.id?.endsWith(".response") == true) {
                responseIds.add(message.parentId!!)
            }
            val parentId = message.parentId
            if (parentId != null && parentId.toInt() >= n - 1) {
                println("Losses ${(1 - responseIds.size.toDouble() / n) * 100}%")
                complete.complete(true)
                cancel()
            }
        }
    }

    scope.launch {
        repeat(n) {
            if (it % 20 == 0) delay(1)
            broadcast(
                MagixMessage(
                    format = "test",
                    payload = JsonObject(emptyMap()),
                    origin = "test",
                    target = "loop",
                    id = it.toString()
                )
            )
        }
    }

    complete.await()
    println("completed")
}


@OptIn(ExperimentalTime::class)
suspend fun main(): Unit = coroutineScope {
    launch(Dispatchers.Default) {
        val server = startMagixServer(enableRawRSocket = false, enableZmq = false) { flow ->
            //echo each message
            flow.onEach { message ->
                if (message.parentId == null) {
                    val m = message.copy(origin = "loop", parentId = message.id, id = message.id + ".response")
                    log.info("echo: $m")
                    flow.emit(m)
                }
            }.launchIn(this)
        }


        val responseTime = measureTime {
            MagixEndpoint.rSocketStreamWithWebSockets("localhost").use {
                it.collectEcho(this, 5000)
            }
        }

        println(responseTime)

        server.stop(500, 500)
        cancel()
    }
}