package ru.mipt.npm.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Inwards API of magix endpoint used to build services
 */
public interface MagixEndpoint {

    /**
     * Subscribe to a [Flow] of messages
     */
    public fun subscribe(
        filter: MagixMessageFilter = MagixMessageFilter.ALL,
    ): Flow<MagixMessage>


    /**
     * Send an event
     */
    public suspend fun broadcast(
        message: MagixMessage,
    )

    /**
     * Close the endpoint and the associated connection if it exists
     */
    public fun close()

    public companion object {
        /**
         * A default port for HTTP/WS connections
         */
        public const val DEFAULT_MAGIX_HTTP_PORT: Int = 7777

        /**
         * A default port for raw TCP connections
         */
        public const val DEFAULT_MAGIX_RAW_PORT: Int = 7778

        /**
         * A default PUB port for ZMQ connections
         */
        public const val DEFAULT_MAGIX_ZMQ_PUB_PORT: Int = 7781

        /**
         * A default PULL port for ZMQ connections
         */
        public const val DEFAULT_MAGIX_ZMQ_PULL_PORT: Int = 7782


        public val magixJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}