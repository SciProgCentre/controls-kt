package space.kscience.controls.ports

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.consumeEachBufferRange
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.writeAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext

public class KtorTcpPort internal constructor(
    context: Context,
    public val host: String,
    public val port: Int,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractPort(context, coroutineContext), Closeable {

    override fun toString(): String = "port[tcp:$host:$port]"

    private val futureSocket = scope.async {
        aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(host, port)
    }

    private val writeChannel = scope.async {
        futureSocket.await().openWriteChannel(true)
    }

    private val listenerJob = scope.launch {
        val input = futureSocket.await().openReadChannel()
        input.consumeEachBufferRange { buffer, _ ->
            val array = ByteArray(buffer.remaining())
            buffer.get(array)
            receive(array)
            isActive
        }
    }

    override suspend fun write(data: ByteArray) {
        writeChannel.await().writeAvailable(data)
    }

    override fun close() {
        listenerJob.cancel()
        futureSocket.cancel()
        super.close()
    }

    public companion object : PortFactory {

        override val type: String = "tcp"

        public fun open(
            context: Context,
            host: String,
            port: Int,
            coroutineContext: CoroutineContext = context.coroutineContext,
        ): KtorTcpPort {
            return KtorTcpPort(context, host, port, coroutineContext)
        }

        override fun build(context: Context, meta: Meta): Port {
            val host = meta["host"].string ?: "localhost"
            val port = meta["port"].int ?: error("Port value for TCP port is not defined in $meta")
            return open(context, host, port)
        }
    }
}