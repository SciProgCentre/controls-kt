package hep.dataforge.magix.service

import hep.dataforge.magix.api.MagixEndpoint
import hep.dataforge.magix.api.MagixMessage
import hep.dataforge.magix.api.MagixMessageFilter
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.util.KtorExperimentalAPI
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.rSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString

public class RSocketMagixEndpoint(
    override val scope: CoroutineScope,
    public val rSocket: RSocket,
) : MagixEndpoint {

    override suspend fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter,
    ): Flow<MagixMessage<T>> {
        val serializer = MagixMessage.serializer(payloadSerializer)
        val payload = Payload(MagixEndpoint.magixJson.encodeToString(filter))
        val flow = rSocket.requestStream(payload)
        return flow.map { MagixEndpoint.magixJson.decodeFromString(serializer, it.data.readText()) }
    }

    override suspend fun <T> broadcast(payloadSerializer: KSerializer<T>, message: MagixMessage<T>) {
        scope.launch {
            val serializer = MagixMessage.serializer(payloadSerializer)
            val payload = Payload(MagixEndpoint.magixJson.encodeToString(serializer, message))
            rSocket.fireAndForget(payload)
        }
    }

    public companion object {

        internal fun buildConnector(rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit) =
            RSocketConnector {
                reconnectable(10)
                connectionConfig(rSocketConfig)
            }

        @OptIn(KtorExperimentalAPI::class)
        public suspend fun withWebSockets(
            scope: CoroutineScope,
            host: String,
            port: Int,
            path: String = "/rsocket",
            rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
        ): RSocketMagixEndpoint {
            val client = HttpClient {
                install(WebSockets)
                install(RSocketSupport) {
                    connector = buildConnector(rSocketConfig)
                }
            }

            val rSocket = client.rSocket(host, port, path)

            //Ensure client is closed after rSocket if finished
            rSocket.job.invokeOnCompletion {
                client.close()
            }

            return RSocketMagixEndpoint(scope, rSocket)
        }
    }
}

