package hep.dataforge.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

/**
 * Inwards API of magix endpoint used to build plugins
 */
public interface MagixEndpoint {
    public val scope: CoroutineScope

    public fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter = MagixMessageFilter.ALL,
    ): Flow<MagixMessage<T>>

    public suspend fun <T> send(
        payloadSerializer: KSerializer<T>,
        message: MagixMessage<T>
    )
}