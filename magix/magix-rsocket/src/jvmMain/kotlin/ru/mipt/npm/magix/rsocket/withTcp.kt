package ru.mipt.npm.magix.rsocket

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.transport.ktor.clientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import ru.mipt.npm.magix.api.MagixEndpoint
import kotlin.coroutines.coroutineContext


/**
 * Create a plain TCP based [RSocketMagixEndpoint] connected to [host] and [port]
 */
public suspend fun <T> MagixEndpoint.Companion.rSocketWithTcp(
    host: String,
    payloadSerializer: KSerializer<T>,
    port: Int = DEFAULT_MAGIX_RAW_PORT,
    tcpConfig: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketMagixEndpoint<T> {
    val transport = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().clientTransport(host, port, tcpConfig)
    val rSocket = buildConnector(rSocketConfig).connect(transport)

    return RSocketMagixEndpoint(payloadSerializer, rSocket, coroutineContext)
}
