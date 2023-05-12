package space.kscience.magix.rsocket

import io.ktor.network.sockets.SocketOptions
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.transport.ktor.tcp.TcpClientTransport
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessageFilter
import kotlin.coroutines.coroutineContext


/**
 * Create a plain TCP based [RSocketMagixEndpoint] connected to [host] and [port]
 */
public suspend fun MagixEndpoint.Companion.rSocketWithTcp(
    host: String,
    port: Int = DEFAULT_MAGIX_RAW_PORT,
    tcpConfig: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketMagixEndpoint {
    val transport = TcpClientTransport(
        hostname = host,
        port = port,
        configure = tcpConfig
    )
    val rSocket = buildConnector(rSocketConfig).connect(transport)

    return RSocketMagixEndpoint(rSocket)
}


public suspend fun MagixEndpoint.Companion.rSocketStreamWithTcp(
    host: String,
    port: Int = DEFAULT_MAGIX_RAW_PORT,
    filter: MagixMessageFilter = MagixMessageFilter.ALL,
    tcpConfig: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketStreamMagixEndpoint {
    val transport = TcpClientTransport(
        hostname = host,
        port = port,
        configure = tcpConfig
    )
    val rSocket = buildConnector(rSocketConfig).connect(transport)

    return RSocketStreamMagixEndpoint(rSocket, coroutineContext, filter)
}