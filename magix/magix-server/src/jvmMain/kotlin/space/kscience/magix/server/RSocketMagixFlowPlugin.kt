@file:OptIn(ExperimentalSerializationApi::class)

package space.kscience.magix.server

import io.rsocket.kotlin.ConnectionAcceptor
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketServer
import io.rsocket.kotlin.core.RSocketServerBuilder
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.transport.ktor.tcp.KtorTcpServerTransport
import io.rsocket.kotlin.transport.ktor.tcp.KtorTcpServerTransportBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.io.encodeToSink
import kotlinx.serialization.protobuf.ProtoBuf
import space.kscience.magix.api.*
import space.kscience.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_RAW_PORT

private enum class RSocketMessageEncoding {
    JSON,
    CBOR,
    PROTO
}


private fun Buffer?.inferFormat(): RSocketMessageEncoding = when (val str = this?.readString()) {
    "proto" -> RSocketMessageEncoding.PROTO
    "cbor" -> RSocketMessageEncoding.CBOR
    else -> RSocketMessageEncoding.JSON
}

private fun decodeMessage(buffer: Buffer, format: RSocketMessageEncoding): MagixMessage = when (format) {
    RSocketMessageEncoding.JSON -> MagixEndpoint.magixJson.decodeFromString(
        MagixMessage.serializer(),
        buffer.readString()
    )

    RSocketMessageEncoding.CBOR -> Cbor.decodeFromByteArray(
        MagixMessage.serializer(),
        buffer.readByteArray()
    )

    RSocketMessageEncoding.PROTO -> ProtoBuf.decodeFromByteArray(
        MagixMessage.serializer(),
        buffer.readByteArray()
    )
}


private fun encodeMessage(message: MagixMessage, format: RSocketMessageEncoding): Buffer {
    return when (format) {
        RSocketMessageEncoding.JSON -> Buffer().also { buffer ->
            MagixEndpoint.magixJson.encodeToSink(MagixMessage.serializer(), message, buffer)
        }

        RSocketMessageEncoding.CBOR -> Buffer().also { buffer ->
            buffer.write(Cbor.encodeToByteArray(MagixMessage.serializer(), message))
        }

        RSocketMessageEncoding.PROTO -> Buffer().also { buffer ->
            buffer.write(ProtoBuf.encodeToByteArray(MagixMessage.serializer(), message))
        }
    }
}

/**
 * Raw TCP magix server plugin
 */
public class RSocketMagixFlowPlugin(
    private val serverHost: String = "0.0.0.0",
    private val serverPort: Int = DEFAULT_MAGIX_RAW_PORT,
    private val transportConfiguration: KtorTcpServerTransportBuilder.() -> Unit = {},
    private val rsocketConfiguration: RSocketServerBuilder.() -> Unit = {},
) : MagixFlowPlugin {

    override fun start(
        scope: CoroutineScope,
        receive: Flow<MagixMessage>,
        sendMessage: suspend (MagixMessage) -> Unit,
    ): Job {
        val tcpTransport = KtorTcpServerTransport(
            scope.coroutineContext,
            configure = transportConfiguration
        ).target(serverHost, serverPort)

        return scope.launch {
            RSocketServer(rsocketConfiguration)
                .startServer(tcpTransport, acceptor(scope, receive, sendMessage))
        }
    }

    public companion object {
        public fun acceptor(
            coroutineScope: CoroutineScope,
            receive: Flow<MagixMessage>,
            sendMessage: suspend (MagixMessage) -> Unit,
        ): ConnectionAcceptor = ConnectionAcceptor {
            RSocketRequestHandler(coroutineScope.coroutineContext) {
                //handler for request/stream
                requestStream { request: Payload ->
                    val format = request.metadata.inferFormat()

                    val requestText = request.data.readString()
                    val filter = if (requestText.isBlank()) {
                        MagixMessageFilter.ALL
                    } else MagixEndpoint.magixJson.decodeFromString(
                        MagixMessageFilter.serializer(),
                        requestText
                    )

                    receive.filter(filter).map { message ->
                        buildPayload {
                            data(encodeMessage(message,format))
                        }
                    }
                }
                //single send
                fireAndForget { request: Payload ->
                    val format = request.metadata.inferFormat()
                    val message = decodeMessage(request.data, format)

                    sendMessage(message)
                }
                // bidirectional connection, used for streaming connection
                requestChannel { request: Payload, input: Flow<Payload> ->
                    val format = request.metadata.inferFormat()

                    input.onEach { inputPayload: Payload ->
                        sendMessage(decodeMessage(inputPayload.data, format))
                    }.launchIn(this)

                    val filterText = request.data.readString()

                    val filter = if (filterText.isBlank()) {
                        MagixMessageFilter.ALL
                    } else {
                        MagixEndpoint.magixJson.decodeFromString(MagixMessageFilter.serializer(), filterText)
                    }

                    receive.filter(filter).map { message ->
                        buildPayload {
                            data(encodeMessage(message,format))
                        }
                    }
                }
            }
        }
    }
}