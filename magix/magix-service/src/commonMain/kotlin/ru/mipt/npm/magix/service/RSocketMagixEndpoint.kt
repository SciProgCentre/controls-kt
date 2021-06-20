package space.kscience.dataforge.magix.service

import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
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
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

public class RSocketMagixEndpoint<T>(
    private val coroutineContext: CoroutineContext,
    private val payloadSerializer: KSerializer<T>,
    private val rSocket: RSocket,
) : MagixEndpoint<T> {

    override fun subscribe(
        filter: MagixMessageFilter,
    ): Flow<MagixMessage<T>> {
        val serializer = MagixMessage.serializer(payloadSerializer)
        val payload = buildPayload { data(MagixEndpoint.magixJson.encodeToString(filter)) }
        val flow = rSocket.requestStream(payload)
        return flow.map { MagixEndpoint.magixJson.decodeFromString(serializer, it.data.readText()) }
    }

    override suspend fun broadcast(message: MagixMessage<T>) {
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

        public suspend fun <T> withWebSockets(
            host: String,
            port: Int,
            payloadSerializer: KSerializer<T>,
            path: String = "/rsocket",
            rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
        ): RSocketMagixEndpoint<T> {
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

            return RSocketMagixEndpoint(coroutineContext, payloadSerializer, rSocket)
        }
    }
}