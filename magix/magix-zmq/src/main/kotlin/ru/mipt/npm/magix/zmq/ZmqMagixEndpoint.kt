package ru.mipt.npm.magix.zmq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import kotlin.coroutines.CoroutineContext

public class ZmqMagixEndpoint<T>(
    private val coroutineContext: CoroutineContext,
    payloadSerializer: KSerializer<T>,
    private val address: String,
) : MagixEndpoint<T>, AutoCloseable {
    private val zmqContext = ZContext()

    private val serializer = MagixMessage.serializer(payloadSerializer)

    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage<T>> {
        val socket = zmqContext.createSocket(SocketType.XSUB)
        socket.bind(address)

        val topic = MagixEndpoint.magixJson.encodeToString(filter)
        socket.subscribe(topic)

        return channelFlow {
            var activeFlag = true
            invokeOnClose {
                activeFlag = false
                socket.close()
            }
            while (activeFlag) {
                try {
                    val string = socket.recvStr()
                    val message = MagixEndpoint.magixJson.decodeFromString(serializer, string)
                    send(message)
                } catch (t: Throwable) {
                    socket.close()
                    if (t is ZMQException && t.errorCode == ZMQ.Error.ETERM.code) {
                        activeFlag = false
                    } else {
                        zmqContext.close()
                    }
                }
            }
        }
    }

    private val publishSocket = zmqContext.createSocket(SocketType.XPUB).apply {
        bind(address)
    }

    override suspend fun broadcast(message: MagixMessage<T>): Unit = withContext(Dispatchers.IO) {
        val string = MagixEndpoint.magixJson.encodeToString(serializer, message)
        publishSocket.send(string)
    }

    override fun close() {
        zmqContext.close()
    }
}