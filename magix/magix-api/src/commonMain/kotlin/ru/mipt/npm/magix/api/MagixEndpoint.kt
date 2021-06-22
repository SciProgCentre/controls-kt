package ru.mipt.npm.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Inwards API of magix endpoint used to build services
 */
public interface MagixEndpoint<T> {

    /**
     * Subscribe to a [Flow] of messages
     */
    public fun subscribe(
        filter: MagixMessageFilter = MagixMessageFilter.ALL,
    ): Flow<MagixMessage<T>>


    /**
     * Send an event
     */
    public suspend fun broadcast(
        message: MagixMessage<T>,
    )

    public companion object {
        /**
         * A default port for HTTP/WS connections
         */
        public const val DEFAULT_MAGIX_HTTP_PORT: Int = 7777

        /**
         * A default port for raw TCP connections
         */
        public const val DEFAULT_MAGIX_RAW_PORT: Int = 7778

        public val magixJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}

/**
 * Specialize this raw json endpoint to use specific serializer
 */
public fun <T : Any> MagixEndpoint<JsonElement>.specialize(
    payloadSerializer: KSerializer<T>
): MagixEndpoint<T> = object : MagixEndpoint<T> {
    override fun subscribe(
        filter: MagixMessageFilter
    ): Flow<MagixMessage<T>> = this@specialize.subscribe(filter).map { message ->
        message.replacePayload { payload ->
            MagixEndpoint.magixJson.decodeFromJsonElement(payloadSerializer, payload)
        }
    }

    override suspend fun broadcast(message: MagixMessage<T>) {
        this@specialize.broadcast(
            message.replacePayload { payload ->
                MagixEndpoint.magixJson.encodeToJsonElement(
                    payloadSerializer,
                    payload
                )
            }
        )
    }

}