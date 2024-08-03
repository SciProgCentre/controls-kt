package space.kscience.controls.ports

import kotlinx.coroutines.*
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.coroutines.CoroutineContext

/**
 * A port based on [DatagramSocket] for cases, where [ChannelPort] does not work for some reason
 */
public class UdpSocketPort(
    override val context: Context,
    meta: Meta,
    private val socket: DatagramSocket,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AbstractAsynchronousPort(context, meta, coroutineContext) {

    private var listenerJob: Job? = null

    override fun onOpen() {
        listenerJob = context.launch(Dispatchers.IO) {
            while (isActive) {
                val buf = ByteArray(socket.receiveBufferSize)

                val packet = DatagramPacket(
                    buf,
                    buf.size,
                )
                socket.receive(packet)

                val bytes = packet.data.copyOfRange(
                    packet.offset,
                    packet.offset + packet.length
                )
                receive(bytes)
            }
        }
    }

    override suspend fun stop() {
        listenerJob?.cancel()
        super.stop()
    }

    override val lifecycleState: LifecycleState
        get() = if(listenerJob?.isActive == true) LifecycleState.STARTED else LifecycleState.STOPPED

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val packet = DatagramPacket(
            data,
            data.size,
            socket.remoteSocketAddress
        )
        socket.send(packet)
    }

}