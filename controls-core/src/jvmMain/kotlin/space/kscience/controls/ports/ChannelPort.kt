package space.kscience.controls.ports

import kotlinx.coroutines.*
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ByteChannel
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import kotlin.coroutines.CoroutineContext

/**
 * Copy the contents of this buffer to an array
 */
public fun ByteBuffer.copyToArray(limit: Int = limit()): ByteArray {
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
    meta: Meta,
    coroutineContext: CoroutineContext = context.coroutineContext,
    channelBuilder: suspend () -> ByteChannel,
) : AbstractAsynchronousPort(context, meta, coroutineContext) {

    /**
     * A handler to await port connection
     */
    private val futureChannel: Deferred<ByteChannel> = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        channelBuilder()
    }

    private var listenerJob: Job? = null

    override val lifecycleState: LifecycleState
        get() = if(listenerJob?.isActive == true) LifecycleState.STARTED else LifecycleState.STOPPED

    override fun onOpen() {
        listenerJob = scope.launch(Dispatchers.IO) {
            val channel = futureChannel.await()
            val buffer = ByteBuffer.allocate(1024)
            while (isActive && channel.isOpen) {
                try {
                    val num = channel.read(buffer)
                    if (num > 0) {
                        receive(buffer.copyToArray(num))
                    }
                    if (num < 0) cancel("The input channel is exhausted")
                } catch (ex: Exception) {
                    if (ex is AsynchronousCloseException) {
                        logger.info { "Channel $channel closed" }
                    } else {
                        logger.error(ex) { "Channel read error, retrying in 1 second" }
                        delay(1000)
                    }
                }
            }
        }
    }

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        futureChannel.await().write(ByteBuffer.wrap(data))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun stop() {
        listenerJob?.cancel()
        if (futureChannel.isCompleted) {
            futureChannel.getCompleted().close()
        }
        super.stop()
    }
}

/**
 * A [Factory] for TCP connections
 */
public object TcpPort : Factory<AsynchronousPort> {

    public fun build(
        context: Context,
        host: String,
        port: Int,
        coroutineContext: CoroutineContext = context.coroutineContext,
    ): ChannelPort {
        val meta = Meta {
            "name" put "tcp://$host:$port"
            "type" put "tcp"
            "host" put host
            "port" put port
        }
        return ChannelPort(context, meta, coroutineContext) {
            SocketChannel.open(InetSocketAddress(host, port))
        }
    }

    /**
     * Create and open TCP port
     */
    public suspend fun start(
        context: Context,
        host: String,
        port: Int,
        coroutineContext: CoroutineContext = context.coroutineContext,
    ): ChannelPort = build(context, host, port, coroutineContext).apply { start() }

    override fun build(context: Context, meta: Meta): ChannelPort {
        val host = meta["host"].string ?: "localhost"
        val port = meta["port"].int ?: error("Port value for TCP port is not defined in $meta")
        return build(context, host, port)
    }

}


/**
 * A [Factory] for UDP connections
 */
public object UdpPort : Factory<AsynchronousPort> {

    public fun build(
        context: Context,
        remoteHost: String,
        remotePort: Int,
        localPort: Int? = null,
        localHost: String? = null,
        coroutineContext: CoroutineContext = context.coroutineContext,
    ): ChannelPort {
        val meta = Meta {
            "name" put "udp://$remoteHost:$remotePort"
            "type" put "udp"
            "remoteHost" put remoteHost
            "remotePort" put remotePort
            localHost?.let { "localHost" put it }
            localPort?.let { "localPort" put it }
        }
        return ChannelPort(context, meta, coroutineContext) {
            DatagramChannel.open().apply {
                //bind the channel to a local port to receive messages
                localPort?.let { bind(InetSocketAddress(localHost ?: "localhost", it)) }
                //connect to remote port to send messages
                connect(InetSocketAddress(remoteHost, remotePort.toInt()))
                context.logger.info { "Connected to UDP $remotePort on $remoteHost" }
            }
        }
    }

    /**
     * Connect a datagram channel to a remote host/port. If [localPort] is provided, it is used to bind local port for receiving messages.
     */
    public suspend fun start(
        context: Context,
        remoteHost: String,
        remotePort: Int,
        localPort: Int? = null,
        localHost: String = "localhost",
    ): ChannelPort = build(context, remoteHost, remotePort, localPort, localHost).apply { start() }


    override fun build(context: Context, meta: Meta): ChannelPort {
        val remoteHost by meta.string { error("Remote host is not specified") }
        val remotePort by meta.number { error("Remote port is not specified") }
        val localHost: String? by meta.string()
        val localPort: Int? by meta.int()
        return build(context, remoteHost, remotePort.toInt(), localPort, localHost)
    }
}