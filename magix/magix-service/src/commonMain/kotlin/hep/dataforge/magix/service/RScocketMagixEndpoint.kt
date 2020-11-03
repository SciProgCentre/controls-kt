package hep.dataforge.magix.service

import hep.dataforge.magix.api.MagixEndpoint
import hep.dataforge.magix.api.MagixEndpoint.Companion.magixJson
import hep.dataforge.magix.api.MagixMessage
import hep.dataforge.magix.api.MagixMessageFilter
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.util.KtorExperimentalAPI
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.rSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlin.time.minutes
import kotlin.time.seconds

public class RScocketMagixEndpoint(
    override val scope: CoroutineScope,
    public val host: String,
    public val port: Int,
    public val path: String = "/rsocket",
) : MagixEndpoint {
    //create ktor client
    @OptIn(KtorExperimentalAPI::class)
    private val client = HttpClient {
        install(WebSockets)
        install(RSocketSupport) {
            connector = RSocketConnector {
                reconnectable(10)
                //configure rSocket connector (all values have defaults)
                connectionConfig {
                    keepAlive = KeepAlive(
                        interval = 30.seconds,
                        maxLifetime = 2.minutes
                    )

//                    //payload for setup frame
//                    setupPayload { Payload("hello world") }

                    //mime types
                    payloadMimeType = PayloadMimeType(
                        data = "application/json",
                        metadata = "application/json"
                    )
                }

                //optional acceptor for server requests
                acceptor {
                    RSocketRequestHandler {
                        requestResponse { it } //echo request payload
                    }
                }
            }
        }
    }

    private val rSocket = scope.async {
        client.rSocket(host, port, path)
    }

    override suspend fun <T> subscribe(
        payloadSerializer: KSerializer<T>,
        filter: MagixMessageFilter,
    ): Flow<MagixMessage<T>> {
        val serializer = MagixMessage.serializer(payloadSerializer)
        val payload = Payload(magixJson.encodeToString(filter))
        val flow = rSocket.await().requestStream(payload)
        return flow.map { magixJson.decodeFromString(serializer, it.data.readText()) }
    }

    override suspend fun <T> send(payloadSerializer: KSerializer<T>, message: MagixMessage<T>) {
        scope.launch {
            val serializer = MagixMessage.serializer(payloadSerializer)
            val payload = Payload(magixJson.encodeToString(serializer, message))
            rSocket.await().fireAndForget(payload)
        }
    }
}