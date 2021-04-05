package space.kscience.dataforge.magix.service

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import io.ktor.util.KtorExperimentalAPI
import io.rsocket.kotlin.core.RSocketConnectorBuilder
import io.rsocket.kotlin.transport.ktor.clientTransport
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext


/**
 * Create a plain TCP based [RSocketMagixEndpoint]
 */
@OptIn(KtorExperimentalAPI::class)
public suspend fun RSocketMagixEndpoint.Companion.withTcp(
    host: String,
    port: Int,
    tcpConfig: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
    rSocketConfig: RSocketConnectorBuilder.ConnectionConfigBuilder.() -> Unit = {},
): RSocketMagixEndpoint {
    val transport = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().clientTransport(host, port, tcpConfig)
    val rSocket = buildConnector(rSocketConfig).connect(transport)

    return RSocketMagixEndpoint(coroutineContext, rSocket)
}
