package space.kscience.dataforge.magix.service

import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.util.KtorExperimentalAPI
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.rSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import space.kscience.dataforge.magix.api.MagixEndpoint
import space.kscience.dataforge.magix.api.MagixMessage
import space.kscience.dataforge.magix.api.MagixMessageFilter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

public class RSocketMagixEndpoint(
    private val coroutineContext: CoroutineContext,
    private val rSocket: RSocket,
) : MagixEndpoint {

    override fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter,
    ): Flow<MagixMessage<T>> {
        val serializer = MagixMessage.serializer(payloadSerializer)
        val payload = buildPayload { data(MagixEndpoint.magixJson.encodeToString(filter)) }
        val flow = rSocket.requestStream(payload)
        return flow.map { MagixEndpoint.magixJson.decodeFromString(serializer, it.data.readText()) }
    }

    override suspend fun <T> broadcast(payloadSerializer: KSerializer<T>, message: MagixMessage<T>) {
        withContext(coroutineContext) {
            val serializer = MagixMessage.serializer(payloadSerializer)
            val payload = buildPayload { data(MagixEndpoint.magixJson.encodeToString(serializer, message)) }
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

            return RSocketMagixEndpoint(coroutineContext, rSocket)
        }
    }
}