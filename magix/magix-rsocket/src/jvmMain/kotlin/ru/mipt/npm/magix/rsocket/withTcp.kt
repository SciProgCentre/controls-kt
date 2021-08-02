package ru.mipt.npm.magix.rsocket

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.SocketOptions
import io.ktor.util.InternalAPI
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.transport.ktor.TcpClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import ru.mipt.npm.magix.api.MagixEndpoint
import kotlin.coroutines.coroutineContext


/**
 * Create a plain TCP based [RSocketMagixEndpoint] connected to [host] and [port]
 */
@OptIn(InternalAPI::class)
public suspend fun <T> MagixEndpoint.Companion.rSocketWithTcp(
    host: String,
    payloadSerializer: KSerializer<T>,
    port: Int = DEFAULT_MAGIX_RAW_PORT,
    tcpConfig: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketMagixEndpoint<T> {
    val transport = TcpClientTransport(
        ActorSelectorManager(Dispatchers.IO),
        hostname = host,
        port = port,
        configure = tcpConfig
    )
    val rSocket = buildConnector(rSocketConfig).connect(transport)

    return RSocketMagixEndpoint(payloadSerializer, rSocket, coroutineContext)
}
