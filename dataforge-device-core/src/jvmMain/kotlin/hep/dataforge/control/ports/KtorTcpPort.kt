package hep.dataforge.control.ports

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.consumeEachBufferRange
import io.ktor.utils.io.writeAvailable
import kotlinx.coroutines.*
import mu.KLogger
import mu.KotlinLogging
import java.net.InetSocketAddress

class KtorTcpPort internal constructor(
    scope: CoroutineScope,
    val host: String,
    val port: Int
) : Port(scope), AutoCloseable {

    override val logger: KLogger = KotlinLogging.logger("port[tcp:$host:$port]")

    private val socket = scope.async {
        aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(InetSocketAddress(host, port))
    }

    private val writeChannel = scope.async {
        socket.await().openWriteChannel(true)
    }

    private val listenerJob = scope.launch {
        val input = socket.await().openReadChannel()
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

}

fun CoroutineScope.openKtorTcpPort(host: String, port: Int): TcpPort {
    val scope = CoroutineScope(SupervisorJob(coroutineContext[Job]))
    return TcpPort(scope, host, port)
}