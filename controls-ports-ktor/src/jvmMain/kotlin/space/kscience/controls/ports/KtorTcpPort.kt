package space.kscience.controls.ports

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.read
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext

public class KtorTcpPort internal constructor(
    context: Context,
    meta: Meta,
    public val host: String,
    public val port: Int,
    coroutineContext: CoroutineContext = context.coroutineContext,
    private val socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
) : AbstractAsynchronousPort(context, meta, coroutineContext) {

    override fun toString(): String = "port[tcp:$host:$port]"

    private var writeChannel: ByteWriteChannel? = null
    private var listenerJob: Job? = null

    override suspend fun onOpen() {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)

        val socket = aSocket(selectorManager)
            .tcp().connect(host, port, socketOptions)

        writeChannel = socket.openWriteChannel(true)

        listenerJob = scope.launch {
            val input = socket.openReadChannel()
            while (!input.isClosedForRead && isActive) {
                input.read { arraySource, begin, endExclusive ->
                    val array = arraySource.copyOfRange(begin, endExclusive)
                    receive(array)
                    array.size
                }
            }
        }.apply {
            invokeOnCompletion {
                socket.close()
                selectorManager.close()
                writeChannel = null
            }
        }

    }

    override suspend fun write(data: ByteArray) {
        writeChannel?.writeByteArray(data) ?: error("Port is not open")
    }

    override val lifecycleState: LifecycleState
        get() = if (listenerJob?.isActive == true) LifecycleState.STARTED else LifecycleState.STOPPED

    override suspend fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        super.stop()
    }

    public companion object : Factory<AsynchronousPort> {

        public fun build(
            context: Context,
            host: String,
            port: Int,
            coroutineContext: CoroutineContext = context.coroutineContext,
            socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
        ): KtorTcpPort {
            val meta = Meta {
                "name" put "tcp://$host:$port"
                "type" put "tcp"
                "host" put host
                "port" put port
            }
            return KtorTcpPort(context, meta, host, port, coroutineContext, socketOptions)
        }

        public suspend fun start(
            context: Context,
            host: String,
            port: Int,
            coroutineContext: CoroutineContext = context.coroutineContext,
            socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
        ): KtorTcpPort = build(context, host, port, coroutineContext, socketOptions).apply { start() }

        override fun build(context: Context, meta: Meta): AsynchronousPort {
            val host = meta["host"].string ?: "localhost"
            val port = meta["port"].int ?: error("Port value for TCP port is not defined in $meta")
            return build(context, host, port)
        }
    }
}