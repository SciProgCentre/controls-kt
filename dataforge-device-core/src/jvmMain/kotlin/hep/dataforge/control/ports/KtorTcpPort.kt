package hep.dataforge.control.ports

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.consumeEachBufferRange
import io.ktor.utils.io.writeAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class KtorTcpPort internal constructor(
    parentContext: CoroutineContext,
    val host: String,
    val port: Int
) : AbstractPort(parentContext), AutoCloseable {

    override fun toString() = "port[tcp:$host:$port]"

    private val futureSocket = scope.async {
        aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(InetSocketAddress(host, port))
    }

    private val writeChannel = scope.async {
        futureSocket.await().openWriteChannel(true)
    }

    private val listenerJob = scope.launch {
        val input = futureSocket.await().openReadChannel()
        input.consumeEachBufferRange { buffer, last ->
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

    companion object {
        suspend fun open(host: String, port: Int): KtorTcpPort {
            return KtorTcpPort(coroutineContext, host, port)
        }
    }
}