package space.kscience.magix.server

import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketServer
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.transport.ktor.tcp.TcpServer
import io.rsocket.kotlin.transport.ktor.tcp.TcpServerTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import space.kscience.magix.api.*
import space.kscience.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_RAW_PORT

/**
 * Raw TCP magix server
 */
public class RSocketMagixFlowPlugin(public val port: Int = DEFAULT_MAGIX_RAW_PORT): MagixFlowPlugin {
    override fun start(scope: CoroutineScope, magixFlow: MutableSharedFlow<MagixMessage>): Job {
        val tcpTransport = TcpServerTransport(port = port)
        val rSocketJob: TcpServer = RSocketServer().bindIn(scope, tcpTransport, acceptor(scope, magixFlow))

        scope.coroutineContext[Job]?.invokeOnCompletion {
            rSocketJob.handlerJob.cancel()
        }

        return rSocketJob.handlerJob
    }

    public companion object{
        public fun acceptor(
            coroutineScope: CoroutineScope,
            magixFlow: MutableSharedFlow<MagixMessage>,
        ): ConnectionAcceptor = ConnectionAcceptor {
            RSocketRequestHandler(coroutineScope.coroutineContext) {
                //handler for request/stream
                requestStream { request: Payload ->
                    val filter = MagixEndpoint.magixJson.decodeFromString(MagixMessageFilter.serializer(), request.data.readText())
                    magixFlow.filter(filter).map { message ->
                        val string = MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message)
                        buildPayload { data(string) }
                    }
                }
                //single send
                fireAndForget { request: Payload ->
                    val message = MagixEndpoint.magixJson.decodeFromString(MagixMessage.serializer(), request.data.readText())
                    magixFlow.emit(message)
                }
                // bi-directional connection
                requestChannel { request: Payload, input: Flow<Payload> ->
                    input.onEach {
                        magixFlow.emit(MagixEndpoint.magixJson.decodeFromString(MagixMessage.serializer(), it.data.readText()))
                    }.launchIn(this)

                    val filterText = request.data.readText()

                    val filter = if(filterText.isNotBlank()){
                        MagixEndpoint.magixJson.decodeFromString(MagixMessageFilter.serializer(), filterText)
                    } else {
                        MagixMessageFilter()
                    }

                    magixFlow.filter(filter).map { message ->
                        val string = MagixEndpoint.magixJson.encodeToString(message)
                        buildPayload { data(string) }
                    }
                }
            }
        }
    }
}