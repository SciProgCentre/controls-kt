package space.kscience.controls.ports

import kotlinx.coroutines.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import kotlin.coroutines.CoroutineContext

public fun ByteBuffer.toArray(limit: Int = limit()): ByteArray {
    rewind()
    val response = ByteArray(limit)
    get(response)
    rewind()
    return response
}

/**
 * A port based on nio [ByteChannel]
 */
public class ChannelPort(
    context: Context,
    coroutineContext: CoroutineContext = context.coroutineContext,
    channelBuilder: suspend () -> ByteChannel,
) : AbstractPort(context, coroutineContext), AutoCloseable {

    private val futureChannel: Deferred<ByteChannel> = this.scope.async(Dispatchers.IO) {
        channelBuilder()
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
                    receive(buffer.toArray(num))
                }
                if (num < 0) cancel("The input channel is exhausted")
            } catch (ex: Exception) {
                logger.error(ex) { "Channel read error" }
                delay(1000)
            }
        }
    }

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        futureChannel.await().write(ByteBuffer.wrap(data))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun close() {
        listenerJob.cancel()
        if (futureChannel.isCompleted) {
            futureChannel.getCompleted().close()
        } else {
            futureChannel.cancel()
        }
        super.close()
    }
}

/**
 * A [PortFactory] for TCP connections
 */
public object TcpPort : PortFactory {

    override val type: String = "tcp"

    public fun open(
        context: Context,
        host: String,
        port: Int,
        coroutineContext: CoroutineContext = context.coroutineContext,
    ): ChannelPort = ChannelPort(context, coroutineContext) {
        SocketChannel.open(InetSocketAddress(host, port))
    }

    override fun build(context: Context, meta: Meta): ChannelPort {
        val host = meta["host"].string ?: "localhost"
        val port = meta["port"].int ?: error("Port value for TCP port is not defined in $meta")
        return open(context, host, port)
    }
}


/**
 * A [PortFactory] for UDP connections
 */
public object UdpPort : PortFactory {

    override val type: String = "udp"

    /**
     * Connect a datagram channel to a remote host/port. If [localPort] is provided, it is used to bind local port for receiving messages.
     */
    public fun open(
        context: Context,
        remoteHost: String,
        remotePort: Int,
        localPort: Int? = null,
        localHost: String = "localhost",
        coroutineContext: CoroutineContext = context.coroutineContext,
    ): ChannelPort = ChannelPort(context, coroutineContext) {
        DatagramChannel.open().apply {
            //bind the channel to a local port to receive messages
            localPort?.let { bind(InetSocketAddress(localHost, localPort)) }
            //connect to remote port to send messages
            connect(InetSocketAddress(remoteHost, remotePort))
        }
    }

    override fun build(context: Context, meta: Meta): ChannelPort {
        val remoteHost by meta.string { error("Remote host is not specified") }
        val remotePort by meta.number { error("Remote port is not specified") }
        val localHost: String? by meta.string()
        val localPort: Int? by meta.int()
        return open(context, remoteHost, remotePort.toInt(), localPort, localHost ?: "localhost")
    }
}