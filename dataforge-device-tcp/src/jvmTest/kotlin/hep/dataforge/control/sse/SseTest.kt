package hep.dataforge.control.sse

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.response.cacheControl
import io.ktor.response.respondBytesWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.junit.jupiter.api.Test

@OptIn(KtorExperimentalAPI::class)
suspend fun ApplicationCall.respondSse(events: Flow<SseEvent>) {
    response.cacheControl(CacheControl.NoCache(null))
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeSseFlow(events)
    }
}

suspend fun HttpClient.readSse(address: String, block: suspend (SseEvent) -> Unit): Job = launch {
    get<HttpStatement>(address).execute { response: HttpResponse ->
        // Response is not downloaded here.
        val channel = response.receive<ByteReadChannel>()
        val flow = channel.readSseFlow()
        flow.collect(block)
    }
}

class SseTest {
    @OptIn(KtorExperimentalAPI::class)
    @Test
    fun testSseIntegration() {
        runBlocking(Dispatchers.Default) {
            val server = embeddedServer(CIO, 12080) {
                routing {
                    get("/") {
                        val flow = flow {
                            repeat(5) {
                                delay(300)
                                emit(it)
                            }
                        }.map {
                            SseEvent(data = it.toString(), id = it.toString())
                        }
                        call.respondSse(flow)
                    }
                }
            }
            server.start(wait = false)
            delay(1000)
            val client = HttpClient(io.ktor.client.engine.cio.CIO)
            client.readSse("http://localhost:12080") {
                println(it)
            }
            delay(2000)
            println("Closing the client after waiting")
            client.close()
            server.stop(1000, 1000)
        }
    }
}