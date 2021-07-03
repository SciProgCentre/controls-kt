package ru.mipt.npm.magix.zmq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import ru.mipt.npm.magix.api.filter
import kotlin.coroutines.CoroutineContext

public class ZmqMagixEndpoint<T>(
    private val host: String,
    payloadSerializer: KSerializer<T>,
    private val pubPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PUB_PORT,
    private val pullPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PULL_PORT,
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) : MagixEndpoint<T>, AutoCloseable {
    private val zmqContext by lazy { ZContext() }

    private val serializer = MagixMessage.serializer(payloadSerializer)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage<T>> {
        val socket = zmqContext.createSocket(SocketType.SUB)
        socket.connect("$host:$pubPort")
        socket.subscribe("")

        return channelFlow {
            var activeFlag = true
            invokeOnClose {
                activeFlag = false
                socket.close()
            }
            while (activeFlag) {
                try {
                    //This is a blocking call.
                    val string: String? = socket.recvStr(zmq.ZMQ.ZMQ_DONTWAIT)
                    if (string != null) {
                        val message = MagixEndpoint.magixJson.decodeFromString(serializer, string)
                        send(message)
                    }
                } catch (t: Throwable) {
                    socket.close()
                    if (t is ZMQException && t.errorCode == ZMQ.Error.ETERM.code) {
                        activeFlag = false
                    } else {
                        zmqContext.close()
                    }
                }
            }
        }.filter(filter).flowOn(coroutineContext) //should be flown on IO because of blocking calls
    }

    private val publishSocket by lazy {
        zmqContext.createSocket(SocketType.PUSH).apply {
            connect("$host:$pullPort")
        }
    }

    override suspend fun broadcast(message: MagixMessage<T>): Unit = withContext(coroutineContext) {
        val string = MagixEndpoint.magixJson.encodeToString(serializer, message)
        publishSocket.send(string)
    }

    override fun close() {
        zmqContext.close()
    }
}