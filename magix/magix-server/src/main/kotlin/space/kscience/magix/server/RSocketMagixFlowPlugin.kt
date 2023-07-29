package space.kscience.magix.server

import io.ktor.network.sockets.SocketOptions
import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketServer
import io.rsocket.kotlin.core.RSocketServerBuilder
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.transport.ktor.tcp.TcpServer
import io.rsocket.kotlin.transport.ktor.tcp.TcpServerTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import space.kscience.magix.api.*
import space.kscience.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_RAW_PORT

/**
 * Raw TCP magix server plugin
 */
public class RSocketMagixFlowPlugin(
    private val serverHost: String = "0.0.0.0",
    private val serverPort: Int = DEFAULT_MAGIX_RAW_PORT,
    private val transportConfiguration: SocketOptions.AcceptorOptions.() -> Unit = {},
    private val rsocketConfiguration: RSocketServerBuilder.() -> Unit = {},
) : MagixFlowPlugin {

    override fun start(
        scope: CoroutineScope,
        receive: Flow<MagixMessage>,
        sendMessage: suspend (MagixMessage) -> Unit,
    ): Job {
        val tcpTransport = TcpServerTransport(
            hostname = serverHost,
            port = serverPort,
            configure = transportConfiguration
        )
        val rSocketJob: TcpServer = RSocketServer(rsocketConfiguration)
            .bindIn(scope, tcpTransport, acceptor(scope, receive, sendMessage))

        scope.coroutineContext[Job]?.invokeOnCompletion {
            rSocketJob.handlerJob.cancel()
        }

        return rSocketJob.handlerJob
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
                    val filter = MagixEndpoint.magixJson.decodeFromString(
                        MagixMessageFilter.serializer(),
                        request.data.readText()
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
                        request.data.readText()
                    )

                    sendMessage(message)
                }
                // bidirectional connection, used for streaming connection
                requestChannel { request: Payload, input: Flow<Payload> ->
                    input.onEach { inputPayload ->
                        sendMessage(
                            MagixEndpoint.magixJson.decodeFromString(
                                MagixMessage.serializer(),
                                inputPayload.use { it.data.readText() }
                            )
                        )
                    }.launchIn(this)

                    val filterText = request.use { it.data.readText() }

                    val filter = if (filterText.isNotBlank()) {
                        MagixEndpoint.magixJson.decodeFromString(MagixMessageFilter.serializer(), filterText)
                    } else {
                        MagixMessageFilter()
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