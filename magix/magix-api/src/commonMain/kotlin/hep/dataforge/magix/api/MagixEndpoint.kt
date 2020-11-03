package hep.dataforge.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Inwards API of magix endpoint used to build plugins
 */
public interface MagixEndpoint {
    public val scope: CoroutineScope

    public suspend fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter = MagixMessageFilter.ALL,
    ): Flow<MagixMessage<T>>

    public suspend fun <T> send(
        payloadSerializer: KSerializer<T>,
        message: MagixMessage<T>
    )

    public companion object{
        public const val DEFAULT_MAGIX_WS_PORT: Int = 7777
        public const val DEFAULT_MAGIX_RAW_PORT: Int = 7778
        public val magixJson: Json = Json
    }
}