package space.kscience.magix.client

import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.coroutines.runBlocking
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.rsocket.rSocketWithTcp
import space.kscience.magix.rsocket.rSocketWithWebSockets
import java.util.concurrent.Flow

internal class KMagixEndpoint<T>(
    private val endpoint: MagixEndpoint,
    private val filter: MagixMessageFilter,
) : JMagixEndpoint<T> {

    override fun broadcast(msg: MagixMessage): Unit = runBlocking {
        endpoint.broadcast(msg)
    }

    override fun subscribe(): Flow.Publisher<MagixMessage> = endpoint.subscribe(filter).asPublisher()

    companion object {

        fun <T> rSocketTcp(
            host: String,
            port: Int,
        ): KMagixEndpoint<T> {
            val endpoint = runBlocking {
                MagixEndpoint.rSocketWithTcp(host, port)
            }
            return KMagixEndpoint(endpoint, MagixMessageFilter())
        }

        fun <T> rSocketWs(
            host: String,
            port: Int,
            path: String = "/rsocket"
        ): KMagixEndpoint<T> {
            val endpoint = runBlocking {
                MagixEndpoint.rSocketWithWebSockets(host, port, path)
            }
            return KMagixEndpoint(endpoint, MagixMessageFilter())
        }
    }
}