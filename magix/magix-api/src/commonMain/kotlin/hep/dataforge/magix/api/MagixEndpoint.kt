package hep.dataforge.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Inwards API of magix endpoint used to build services
 */
public interface MagixEndpoint {
    public val scope: CoroutineScope

    /**
     * Subscribe to a [Flow] of messages using specific [payloadSerializer]
     */
    public suspend fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter = MagixMessageFilter.ALL,
    ): Flow<MagixMessage<T>>


    /**
     * Send an event using specific [payloadSerializer]
     */
    public suspend fun <T> send(
        payloadSerializer: KSerializer<T>,
        message: MagixMessage<T>,
    )

    public companion object {
        public const val DEFAULT_MAGIX_WS_PORT: Int = 7777
        public const val DEFAULT_MAGIX_RAW_PORT: Int = 7778
        public val magixJson: Json = Json
    }
}

public suspend fun MagixEndpoint.subscribe(
    filter: MagixMessageFilter = MagixMessageFilter.ALL,
): Flow<MagixMessage<JsonElement>> = subscribe(JsonElement.serializer())

public suspend fun MagixEndpoint.send(message: MagixMessage<JsonElement>): Unit =
    send(JsonElement.serializer(), message)