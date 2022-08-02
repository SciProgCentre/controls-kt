package space.kscience.magix.client

import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.coroutines.runBlocking
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.rsocket.rSocketWithTcp
import space.kscience.magix.rsocket.rSocketWithWebSockets
import java.util.concurrent.Flow

internal class ControlsMagixClient<T>(
    private val endpoint: MagixEndpoint,
    private val filter: MagixMessageFilter,
) : MagixClient<T> {

    override fun broadcast(msg: MagixMessage): Unit = runBlocking {
        endpoint.broadcast(msg)
    }

    override fun subscribe(): Flow.Publisher<MagixMessage> = endpoint.subscribe(filter).asPublisher()

    companion object {

        fun <T> rSocketTcp(
            host: String,
            port: Int,
        ): ControlsMagixClient<T> {
            val endpoint = runBlocking {
                MagixEndpoint.rSocketWithTcp(host, port)
            }
            return ControlsMagixClient(endpoint, MagixMessageFilter())
        }

        fun <T> rSocketWs(
            host: String,
            port: Int,
            path: String = "/rsocket"
        ): ControlsMagixClient<T> {
            val endpoint = runBlocking {
                MagixEndpoint.rSocketWithWebSockets(host, port, path)
            }
            return ControlsMagixClient(endpoint, MagixMessageFilter())
        }
    }
}