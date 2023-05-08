package space.kscience.magix.rsocket

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.utils.io.core.Closeable
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.filter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * RSocket endpoint based on an established channel. This way it works a bit faster than [RSocketMagixEndpoint]
 * for sending and receiving, but less flexible in terms of filters. One general [streamFilter] could be set
 * in constructor and applied on the loop side. Filters in [subscribe] are applied on the endpoint side on top
 * of received data.
 */
public class RSocketStreamMagixEndpoint(
    private val rSocket: RSocket,
    private val coroutineContext: CoroutineContext,
    public val streamFilter: MagixMessageFilter = MagixMessageFilter(),
) : MagixEndpoint, Closeable {

    private val output: MutableSharedFlow<MagixMessage> = MutableSharedFlow()

    private val input: Flow<Payload> by lazy {
        rSocket.requestChannel(
            buildPayload {
                data(
                    MagixEndpoint.magixJson.encodeToString(
                        MagixMessageFilter.serializer(),
                        streamFilter
                    )
                )
            },
            output.map { message ->
                buildPayload {
                    data(MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message))
                }
            }.flowOn(coroutineContext[CoroutineDispatcher] ?: Dispatchers.Unconfined)
        )
    }

    override fun subscribe(
        filter: MagixMessageFilter,
    ): Flow<MagixMessage> {
        return input.map {
            MagixEndpoint.magixJson.decodeFromString(MagixMessage.serializer(), it.data.readText())
        }.filter(filter).flowOn(coroutineContext[CoroutineDispatcher] ?: Dispatchers.Unconfined)
    }

    override suspend fun broadcast(message: MagixMessage): Unit {
        output.emit(message)
    }

    override fun close() {
        rSocket.cancel()
    }
}

public suspend fun MagixEndpoint.Companion.rSocketStreamWithWebSockets(
    host: String,
    port: Int = DEFAULT_MAGIX_HTTP_PORT,
    path: String = "/rsocket",
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketStreamMagixEndpoint {
    val client = HttpClient {
        install(WebSockets)
        install(RSocketSupport) {
            connector = buildConnector(rSocketConfig)
        }
    }

    val rSocket = client.rSocket(host, port, path)

    //Ensure client is closed after rSocket if finished
    rSocket.coroutineContext[Job]?.invokeOnCompletion {
        client.close()
    }

    return RSocketStreamMagixEndpoint(rSocket, coroutineContext)
}