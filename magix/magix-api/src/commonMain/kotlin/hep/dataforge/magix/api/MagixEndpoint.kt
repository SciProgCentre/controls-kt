package hep.dataforge.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Inwards API of magix endpoint used to build services
 */
public interface MagixEndpoint {
    /**
     * Subscribe to a [Flow] of messages using specific [payloadSerializer]
     */
    public fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter = MagixMessageFilter.ALL,
    ): Flow<MagixMessage<T>>


    /**
     * Send an event using specific [payloadSerializer]
     */
    public suspend fun <T> broadcast(
        payloadSerializer: KSerializer<T>,
        message: MagixMessage<T>,
    )

    public companion object {
        public const val DEFAULT_MAGIX_WS_PORT: Int = 7777
        public const val DEFAULT_MAGIX_RAW_PORT: Int = 7778
        public val magixJson: Json = Json{
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}

public fun MagixEndpoint.subscribe(
    filter: MagixMessageFilter = MagixMessageFilter.ALL,
): Flow<MagixMessage<JsonElement>> = subscribe(JsonElement.serializer(),filter)

public suspend fun MagixEndpoint.broadcast(message: MagixMessage<JsonElement>): Unit =
    broadcast(JsonElement.serializer(), message)