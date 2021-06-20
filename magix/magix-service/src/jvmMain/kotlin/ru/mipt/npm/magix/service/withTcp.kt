package space.kscience.dataforge.magix.service

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.transport.ktor.clientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlin.coroutines.coroutineContext


/**
 * Create a plain TCP based [RSocketMagixEndpoint]
 */
public suspend fun <T> RSocketMagixEndpoint.Companion.withTcp(
    host: String,
    port: Int,
    payloadSerializer: KSerializer<T>,
    tcpConfig: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketMagixEndpoint<T> {
    val transport = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().clientTransport(host, port, tcpConfig)
    val rSocket = buildConnector(rSocketConfig).connect(transport)

    return RSocketMagixEndpoint(coroutineContext, payloadSerializer, rSocket)
}
