package ru.mipt.npm.magix.zmq

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter
import ru.mipt.npm.magix.api.filter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

public class ZmqMagixEndpoint(
    private val host: String,
    private val protocol: String,
    private val pubPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PUB_PORT,
    private val pullPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PULL_PORT,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : MagixEndpoint, AutoCloseable {
    private val zmqContext by lazy { ZContext() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> {
        val socket = zmqContext.createSocket(SocketType.SUB)
        socket.connect("$protocol://$host:$pubPort")
        socket.subscribe("")

        return channelFlow {
            invokeOnClose {
                socket.close()
            }
            while (isActive) {
                try {
                    //This is a blocking call.
                    val string: String? = socket.recvStr()
                    if (string != null) {
                        val message = MagixEndpoint.magixJson.decodeFromString(MagixMessage.serializer(), string)
                        send(message)
                    }
                } catch (t: Throwable) {
                    socket.close()
                    if (t is ZMQException && t.errorCode == ZMQ.Error.ETERM.code) {
                        cancel("ZMQ connection terminated", t)
                    } else {
                        throw t
                    }
                }
            }
        }.filter(filter).flowOn(coroutineContext[CoroutineDispatcher] ?: Dispatchers.IO)
        //should be flown on IO because of blocking calls
    }

    private val publishSocket by lazy {
        zmqContext.createSocket(SocketType.PUSH).apply {
            connect("$protocol://$host:$pullPort")
        }
    }

    override suspend fun broadcast(message: MagixMessage): Unit = withContext(coroutineContext) {
        val string = MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message)
        publishSocket.send(string)
    }

    override fun close() {
        zmqContext.close()
    }
}

public suspend fun MagixEndpoint.Companion.zmq(
    host: String,
    protocol: String = "tcp",
    pubPort: Int = DEFAULT_MAGIX_ZMQ_PUB_PORT,
    pullPort: Int = DEFAULT_MAGIX_ZMQ_PULL_PORT,
): ZmqMagixEndpoint = ZmqMagixEndpoint(
    host,
    protocol,
    pubPort,
    pullPort,
    coroutineContext = coroutineContext
)