package space.kscience.controls.ports

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.UnsafeIoApi
import kotlinx.io.readByteArray
import kotlinx.io.unsafe.UnsafeBufferOperations
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.number
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext

@OptIn(UnsafeIoApi::class)
private fun ByteArray.asSource(): Source = Buffer().apply { UnsafeBufferOperations.moveToTail(this, this@asSource) }

public class KtorUdpPort internal constructor(
    context: Context,
    meta: Meta,
    public val remoteHost: String,
    public val remotePort: Int,
    public val localPort: Int? = null,
    public val localHost: String = "localhost",
    coroutineContext: CoroutineContext = context.coroutineContext,
    socketOptions: SocketOptions.UDPSocketOptions.() -> Unit = {},
) : AbstractAsynchronousPort(context, meta, coroutineContext) {

    override fun toString(): String = "port[udp:$remoteHost:$remotePort]"

    private val futureSocket = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        aSocket(ActorSelectorManager(Dispatchers.IO)).udp().connect(
            remoteAddress = InetSocketAddress(remoteHost, remotePort),
            localAddress = localPort?.let { InetSocketAddress(localHost, localPort) },
            configure = socketOptions
        )
    }

//    private val writeChannel= scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
//        futureSocket.await().outgoing
//    }

    private var listenerJob: Job? = null

    override fun onOpen() {
        listenerJob = scope.launch {
            val input = futureSocket.await().incoming
            for (datagram in input) {
                receive(datagram.packet.readByteArray())
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        val socket = futureSocket.await()
        socket.send(Datagram(data.asSource(), socket.remoteAddress))
    }

    override val lifecycleState: LifecycleState
        get() = if (listenerJob?.isActive == true) LifecycleState.STARTED else LifecycleState.STOPPED

    override suspend fun stop() {
        listenerJob?.cancel()
        futureSocket.cancel()
        super.stop()
    }

    public companion object : Factory<AsynchronousPort> {

        public fun build(
            context: Context,
            remoteHost: String,
            remotePort: Int,
            localPort: Int? = null,
            localHost: String? = null,
            coroutineContext: CoroutineContext = context.coroutineContext,
            socketOptions: SocketOptions.UDPSocketOptions.() -> Unit = {},
        ): KtorUdpPort {
            val meta = Meta {
                "name" put "udp://$remoteHost:$remotePort"
                "type" put "udp"
                "remoteHost" put remoteHost
                "remotePort" put remotePort
                localHost?.let { "localHost" put it }
                localPort?.let { "localPort" put it }
            }
            return KtorUdpPort(
                context = context,
                meta = meta,
                remoteHost = remoteHost,
                remotePort = remotePort,
                localPort = localPort,
                localHost = localHost ?: "localhost",
                coroutineContext = coroutineContext,
                socketOptions = socketOptions
            )
        }

        /**
         * Create and open UDP port
         */
        public suspend fun start(
            context: Context,
            remoteHost: String,
            remotePort: Int,
            localPort: Int? = null,
            localHost: String = "localhost",
            coroutineContext: CoroutineContext = context.coroutineContext,
            socketOptions: SocketOptions.UDPSocketOptions.() -> Unit = {},
        ): KtorUdpPort = build(
            context,
            remoteHost,
            remotePort,
            localPort,
            localHost,
            coroutineContext,
            socketOptions
        ).apply { start() }

        override fun build(context: Context, meta: Meta): AsynchronousPort {
            val remoteHost by meta.string { error("Remote host is not specified") }
            val remotePort by meta.number { error("Remote port is not specified") }
            val localHost: String? by meta.string()
            val localPort: Int? by meta.int()
            return build(context, remoteHost, remotePort.toInt(), localPort, localHost ?: "localhost")
        }
    }
}