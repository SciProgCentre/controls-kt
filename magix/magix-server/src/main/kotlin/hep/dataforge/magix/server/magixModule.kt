package hep.dataforge.magix.server

import hep.dataforge.magix.api.MagixMessage
import hep.dataforge.magix.api.MagixMessageFilter
import hep.dataforge.magix.api.filter
import io.ktor.application.*
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.html.respondHtml
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.request.receive
import io.ktor.response.cacheControl
import io.ktor.response.respondBytesWriter
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.getValue
import io.ktor.websocket.WebSockets
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketServerSupport
import io.rsocket.kotlin.core.rSocket
import io.rsocket.kotlin.payload.Payload
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.html.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.mipt.npm.ktor.sse.SseEvent
import ru.mipt.npm.ktor.sse.writeSseFlow

public typealias GenericMagixMessage = MagixMessage<JsonElement>

private val genericMessageSerializer: KSerializer<MagixMessage<JsonElement>> =
    MagixMessage.serializer(JsonElement.serializer())

@OptIn(KtorExperimentalAPI::class)
public suspend fun ApplicationCall.respondSse(events: Flow<SseEvent>) {
    response.cacheControl(CacheControl.NoCache(null))
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeSseFlow(events)
    }
}


/**
 * Create a message filter from call parameters
 */
@OptIn(KtorExperimentalAPI::class)
private fun ApplicationCall.buildFilter(): MagixMessageFilter {
    val query = request.queryParameters

    if (query.isEmpty()) {
        return MagixMessageFilter.ALL
    }

    val format: List<String>? by query
    val origin: List<String>? by query
    return MagixMessageFilter(
        format,
        origin
    )
}

public fun Application.magixModule(magixFlow: MutableSharedFlow<GenericMagixMessage>, route: String = "/") {
    if (featureOrNull(WebSockets) == null) {
        install(WebSockets)
    }

    if (featureOrNull(CORS) == null) {
        install(CORS) {
            //TODO consider more safe policy
            anyHost()
        }
    }
    if (featureOrNull(ContentNegotiation) == null) {
        install(ContentNegotiation) {
            json()
        }
    }

    if (featureOrNull(RSocketServerSupport) == null) {
        install(RSocketServerSupport)
    }

    routing {
        route(route) {
            post {
                val message = call.receive<GenericMagixMessage>()
                magixFlow.emit(message)
            }
            get {
                call.respondHtml {
                    body {
                        h1 { +"Magix stream statistics" }
                        h2 { +"Number of subscribers: ${magixFlow.subscriptionCount.value}" }
                        h3 { +"Replay cache size: ${magixFlow.replayCache.size}" }
                        h3 { +"Replay cache:" }
                        ol {
                            magixFlow.replayCache.forEach { message ->
                                li {
                                    code {
                                        +Json.encodeToString(genericMessageSerializer, message)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            //SSE server. Filter from query
            get("sse") {
                val filter = call.buildFilter()
                var idCounter = 0
                val sseFlow = magixFlow.filter(filter).map {
                    val data = Json.encodeToString(genericMessageSerializer, it)
                    SseEvent(data, id = idCounter++.toString())
                }
                call.respondSse(sseFlow)
            }
            //rSocket server. Filter from Payload
            rSocket("rsocket") {
                RSocketRequestHandler {
                    //handler for request/stream
                    requestStream = { request: Payload ->
                        val filter = Json.decodeFromString(MagixMessageFilter.serializer(), request.data.readText())
                        magixFlow.filter(filter).map { message ->
                            val string = Json.encodeToString(genericMessageSerializer, message)
                            Payload(string)
                        }
                    }
                    fireAndForget = { request: Payload ->
                        val message = Json.decodeFromString(genericMessageSerializer, payload.data.readText())
                        magixFlow.emit(message)
                    }
                }
            }
        }
    }
}

public fun Application.magixModule(route: String = "/", buffer: Int = 100) {
    val magixFlow = MutableSharedFlow<GenericMagixMessage>(
        buffer,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    magixModule(magixFlow, route)
}