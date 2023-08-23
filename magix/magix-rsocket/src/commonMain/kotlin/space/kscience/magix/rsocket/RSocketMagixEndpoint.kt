package space.kscience.magix.rsocket

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.utils.io.core.Closeable
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.filter

public class RSocketMagixEndpoint(private val rSocket: RSocket) : MagixEndpoint, Closeable {

    override fun subscribe(
        filter: MagixMessageFilter,
    ): Flow<MagixMessage> {
        val payload = buildPayload {
            data(MagixEndpoint.magixJson.encodeToString(MagixMessageFilter.serializer(), filter))
        }
        val flow = rSocket.requestStream(payload)
        return flow.map {
            MagixEndpoint.magixJson.decodeFromString(MagixMessage.serializer(), it.data.readText())
        }.filter(filter).flowOn(rSocket.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Unconfined)
    }

    override suspend fun broadcast(message: MagixMessage): Unit {
        val payload = buildPayload {
            data(MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message))
        }
        rSocket.fireAndForget(payload)
    }

    override fun close() {
        rSocket.cancel()
    }

    public companion object
}


internal fun buildConnector(
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit,
) = RSocketConnector {
    reconnectable(5)
    connectionConfig(rSocketConfig)
}

/**
 * Build a websocket based endpoint connected to [host], [port] and given routing [path]
 */
public suspend fun MagixEndpoint.Companion.rSocketWithWebSockets(
    host: String,
    port: Int = DEFAULT_MAGIX_HTTP_PORT,
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

    //Ensure the client is closed after rSocket if finished
    rSocket.coroutineContext[Job]?.invokeOnCompletion {
        client.close()
    }

    return RSocketMagixEndpoint(rSocket)
}