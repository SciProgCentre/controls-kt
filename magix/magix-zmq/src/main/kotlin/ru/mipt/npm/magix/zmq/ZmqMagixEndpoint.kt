package ru.mipt.npm.magix.zmq

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import kotlin.coroutines.CoroutineContext

class ZmqMagixEndpoint<T>(
    private val coroutineContext: CoroutineContext,
    private val payloadSerializer: KSerializer<T>,
) : MagixEndpoint<T> {

    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage<T>> {
        TODO("Not yet implemented")
    }

    override suspend fun broadcast(message: MagixMessage<T>) {
        TODO("Not yet implemented")
    }
}