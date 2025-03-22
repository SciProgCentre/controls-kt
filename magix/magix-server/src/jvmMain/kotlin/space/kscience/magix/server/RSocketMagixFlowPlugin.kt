package space.kscience.magix.server

import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketServer
import io.rsocket.kotlin.core.RSocketServerBuilder
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.transport.ktor.tcp.KtorTcpServerTransport
import io.rsocket.kotlin.transport.ktor.tcp.KtorTcpServerTransportBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.readString
import space.kscience.magix.api.*
import space.kscience.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_RAW_PORT

/**
 * Raw TCP magix server plugin
 */
public class RSocketMagixFlowPlugin(
    private val serverHost: String = "0.0.0.0",
    private val serverPort: Int = DEFAULT_MAGIX_RAW_PORT,
    private val transportConfiguration: KtorTcpServerTransportBuilder.() -> Unit = {},
    private val rsocketConfiguration: RSocketServerBuilder.() -> Unit = {},
) : MagixFlowPlugin {

    override fun start(
        scope: CoroutineScope,
        receive: Flow<MagixMessage>,
        sendMessage: suspend (MagixMessage) -> Unit,
    ): Job {
        val tcpTransport = KtorTcpServerTransport(
            scope.coroutineContext,
            configure = transportConfiguration
        ).target(serverHost, serverPort)

        return scope.launch {
           RSocketServer(rsocketConfiguration)
                .startServer(tcpTransport, acceptor(scope, receive, sendMessage))
        }
    }

    public companion object {
        public fun acceptor(
            coroutineScope: CoroutineScope,
            receive: Flow<MagixMessage>,
            sendMessage: suspend (MagixMessage) -> Unit,
        ): ConnectionAcceptor = ConnectionAcceptor {
            RSocketRequestHandler(coroutineScope.coroutineContext) {
                //handler for request/stream
                requestStream { request: Payload ->
                    val requestText = request.data.readString()
                    val filter = if (requestText.isBlank()) {
                        MagixMessageFilter.ALL
                    } else MagixEndpoint.magixJson.decodeFromString(
                        MagixMessageFilter.serializer(),
                        requestText
                    )

                    receive.filter(filter).map { message ->
                        val string = MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message)
                        buildPayload { data(string) }
                    }
                }
                //single send
                fireAndForget { request: Payload ->
                    val message = MagixEndpoint.magixJson.decodeFromString(
                        MagixMessage.serializer(),
                        request.data.readString()
                    )

                    sendMessage(message)
                }
                // bidirectional connection, used for streaming connection
                requestChannel { request: Payload, input: Flow<Payload> ->
                    input.onEach { inputPayload ->
                        sendMessage(
                            MagixEndpoint.magixJson.decodeFromString(
                                MagixMessage.serializer(),
                                inputPayload.use { it.data.readString() }
                            )
                        )
                    }.launchIn(this)

                    val filterText = request.data.readString()

                    val filter = if (filterText.isBlank()) {
                        MagixMessageFilter.ALL
                    } else {
                        MagixEndpoint.magixJson.decodeFromString(MagixMessageFilter.serializer(), filterText)
                    }

                    receive.filter(filter).map { message ->
                        val string = MagixEndpoint.magixJson.encodeToString(message)
                        buildPayload { data(string) }
                    }
                }
            }
        }
    }
}