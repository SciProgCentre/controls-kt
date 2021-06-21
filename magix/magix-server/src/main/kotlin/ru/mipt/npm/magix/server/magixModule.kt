package ru.mipt.npm.magix.server

import io.ktor.application.*
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.html.respondHtml
import io.ktor.request.receive
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.util.getValue
import io.ktor.websocket.WebSockets
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.transport.ktor.server.RSocketSupport
import io.rsocket.kotlin.transport.ktor.server.rSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.html.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.magixJson
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import ru.mipt.npm.magix.api.filter
import java.util.*

public typealias GenericMagixMessage = MagixMessage<JsonElement>

private val genericMessageSerializer: KSerializer<MagixMessage<JsonElement>> =
    MagixMessage.serializer(JsonElement.serializer())


internal fun CoroutineScope.magixAcceptor(magixFlow: MutableSharedFlow<GenericMagixMessage>) = ConnectionAcceptor {
    RSocketRequestHandler {
        //handler for request/stream
        requestStream { request: Payload ->
            val filter = magixJson.decodeFromString(MagixMessageFilter.serializer(), request.data.readText())
            magixFlow.filter(filter).map { message ->
                val string = magixJson.encodeToString(genericMessageSerializer, message)
                buildPayload { data(string) }
            }
        }
        fireAndForget { request: Payload ->
            val message = magixJson.decodeFromString(genericMessageSerializer, request.data.readText())
            magixFlow.emit(message)
        }
        // bi-directional connection
        requestChannel { request: Payload, input: Flow<Payload> ->
            input.onEach {
                magixFlow.emit(magixJson.decodeFromString(genericMessageSerializer, it.data.readText()))
            }.launchIn(this@magixAcceptor)

            val filter = magixJson.decodeFromString(MagixMessageFilter.serializer(), request.data.readText())

            magixFlow.filter(filter).map { message ->
                val string = magixJson.encodeToString(genericMessageSerializer, message)
                buildPayload { data(string) }
            }
        }
    }
}

/**
 * Create a message filter from call parameters
 */
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

    if (featureOrNull(RSocketSupport) == null) {
        install(RSocketSupport)
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
                                        +magixJson.encodeToString(genericMessageSerializer, message)
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
                val sseFlow = magixFlow.filter(filter).map {
                    val data = magixJson.encodeToString(genericMessageSerializer, it)
                    val id = UUID.randomUUID()
                    SseEvent(data, id = id.toString(), event = "message")
                }
                call.respondSse(sseFlow)
            }
            //rSocket server. Filter from Payload
            rSocket("rsocket", acceptor = magixAcceptor(magixFlow))
        }
    }
}

public fun Application.magixModule(route: String = "/", buffer: Int = 100) {
    val magixFlow = MutableSharedFlow<GenericMagixMessage>(buffer)
    magixModule(magixFlow, route)
}