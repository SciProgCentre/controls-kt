package space.kscience.magix.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.server.util.getValue
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.html.*
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixEndpoint.Companion.magixJson
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.filter
import java.util.*


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

/**
 * Attach magix http/sse and websocket-based rsocket event loop + statistics page to existing [MutableSharedFlow]
 */
public fun Application.magixModule(
    magixFlow: Flow<MagixMessage>,
    send: suspend (MagixMessage) -> Unit,
    route: String = "/",
) {
    if (pluginOrNull(WebSockets) == null) {
        install(WebSockets)
    }

    if (pluginOrNull(RSocketSupport) == null) {
        install(RSocketSupport)
    }

    if(pluginOrNull(ContentNegotiation) == null) {
        install(ContentNegotiation) {
            json(MagixEndpoint.magixJson)
        }
    }


//    if (pluginOrNull(CORS) == null) {
//        install(CORS) {
//            //TODO consider more safe policy
//            anyHost()
//        }
//    }


    routing {
        route(route) {
            if (magixFlow is SharedFlow) {
                get("state") {
                    call.respondHtml {
                        head {
                            meta {
                                httpEquiv = "refresh"
                                content = "2"
                            }
                        }
                        body {
                            h1 { +"Magix loop statistics" }
                            if (magixFlow is MutableSharedFlow) {
                                h2 { +"Number of subscribers: ${magixFlow.subscriptionCount.value}" }
                            }
                            h3 { +"Replay cache size: ${magixFlow.replayCache.size}" }
                            h3 { +"Replay cache:" }
                            ol {
                                magixFlow.replayCache.forEach { message ->
                                    li {
                                        code {
                                            +magixJson.encodeToString(message)
                                        }
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
                    val data = magixJson.encodeToString(it)
                    val id = UUID.randomUUID()
                    SseEvent(data, id = id.toString(), event = "message")
                }
                call.respondSse(sseFlow)
            }
            post("broadcast") {
                val message = call.receive<MagixMessage>()
                send(message)
            }
            //rSocket WS server. Filter from Payload
            rSocket(
                "rsocket",
                acceptor = RSocketMagixFlowPlugin.acceptor(application, magixFlow) { send(it) }
            )
        }
    }
}

public fun Application.magixModule(
    magixFlow: MutableSharedFlow<MagixMessage>,
    route: String = "/",
): Unit = magixModule(magixFlow, { magixFlow.emit(it) }, route)

/**
 * Create a new loop [MutableSharedFlow] with given [buffer] and setup magix module based on it
 */
public fun Application.magixModule(route: String = "/", buffer: Int = 100) {
    val magixFlow = MutableSharedFlow<MagixMessage>(buffer)
    magixModule(magixFlow, route)
}