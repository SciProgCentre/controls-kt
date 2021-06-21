package ru.mipt.npm.magix.client

import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import ru.mipt.npm.magix.rsocket.RSocketMagixEndpoint
import ru.mipt.npm.magix.rsocket.withTcp
import java.util.concurrent.Flow

public class ControlsMagixClient<T>(
    private val endpoint: MagixEndpoint<T>,
    private val filter: MagixMessageFilter,
) : MagixClient<T> {

    override fun broadcast(msg: MagixMessage<T>): Unit = runBlocking {
        endpoint.broadcast(msg)
    }

    override fun subscribe(): Flow.Publisher<MagixMessage<T>> = endpoint.subscribe(filter).asPublisher()

    public companion object {

        public fun <T> rSocketTcp(
            host: String,
            port: Int,
            payloadSerializer: KSerializer<T>
        ): ControlsMagixClient<T> {
            val endpoint = runBlocking {
                RSocketMagixEndpoint.withTcp(host, port, payloadSerializer)
            }
            return ControlsMagixClient(endpoint, MagixMessageFilter())
        }

        public fun <T> rSocketWs(
            host: String,
            port: Int,
            payloadSerializer: KSerializer<T>,
            path: String = "/rsocket"
        ): ControlsMagixClient<T> {
            val endpoint = runBlocking {
                RSocketMagixEndpoint.withWebSockets(host, port, payloadSerializer, path)
            }
            return ControlsMagixClient(endpoint, MagixMessageFilter())
        }
    }
}