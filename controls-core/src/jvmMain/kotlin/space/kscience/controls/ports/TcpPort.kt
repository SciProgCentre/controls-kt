package space.kscience.controls.ports

import kotlinx.coroutines.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.coroutines.CoroutineContext

internal fun ByteBuffer.readArray(limit: Int = limit()): ByteArray {
    rewind()
    val response = ByteArray(limit)
    get(response)
    rewind()
    return response
}

public class TcpPort private constructor(
    context: Context,
    public val host: String,
    public val port: Int,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractPort(context, coroutineContext), AutoCloseable {

    override fun toString(): String = "port[tcp:$host:$port]"

    private val futureChannel: Deferred<SocketChannel> = this.scope.async(Dispatchers.IO) {
        SocketChannel.open(InetSocketAddress(host, port)).apply {
            configureBlocking(false)
        }
    }

    /**
     * A handler to await port connection
     */
    public val startJob: Job get() = futureChannel

    private val listenerJob = this.scope.launch(Dispatchers.IO) {
        val channel = futureChannel.await()
        val buffer = ByteBuffer.allocate(1024)
        while (isActive) {
            try {
                val num = channel.read(buffer)
                if (num > 0) {
                    receive(buffer.readArray(num))
                }
                if (num < 0) cancel("The input channel is exhausted")
            } catch (ex: Exception) {
                logger.error(ex){"Channel read error"}
                delay(1000)
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        futureChannel.await().write(ByteBuffer.wrap(data))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun close() {
        listenerJob.cancel()
        if(futureChannel.isCompleted){
            futureChannel.getCompleted().close()
        } else {
            futureChannel.cancel()
        }
        super.close()
    }

    public companion object : PortFactory {
        public fun open(
            context: Context,
            host: String,
            port: Int,
            coroutineContext: CoroutineContext = context.coroutineContext,
        ): TcpPort {
            return TcpPort(context, host, port, coroutineContext)
        }

        override fun build(context: Context, meta: Meta): Port {
            val host = meta["host"].string ?: "localhost"
            val port = meta["port"].int ?: error("Port value for TCP port is not defined in $meta")
            return open(context, host, port)
        }
    }
}