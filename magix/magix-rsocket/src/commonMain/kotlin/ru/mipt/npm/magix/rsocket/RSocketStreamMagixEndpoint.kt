package ru.mipt.npm.magix.rsocket

import io.ktor.utils.io.core.Closeable
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import ru.mipt.npm.magix.api.filter
import kotlin.coroutines.CoroutineContext

/**
 * RSocket endpoint based on established channel
 */
public class RSocketStreamMagixEndpoint(
    private val rSocket: RSocket,
    private val coroutineContext: CoroutineContext,
) : MagixEndpoint, Closeable {

    private val output: MutableSharedFlow<MagixMessage> = MutableSharedFlow()

    private val input: Flow<Payload> by lazy {
        rSocket.requestChannel(
            Payload.Empty,
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