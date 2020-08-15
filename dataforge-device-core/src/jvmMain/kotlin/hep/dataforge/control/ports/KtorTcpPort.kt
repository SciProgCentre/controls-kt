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
import kotlin.coroutines.coroutineContext

class KtorTcpPort internal constructor(
    scope: CoroutineScope,
    val host: String,
    val port: Int
) : Port(scope), AutoCloseable {

    override val logger: KLogger = KotlinLogging.logger("port[tcp:$host:$port]")

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

    companion object{
        suspend fun open(host: String, port: Int): KtorTcpPort{
            val scope = CoroutineScope(SupervisorJob(coroutineContext[Job]))
            return KtorTcpPort(scope, host, port)
        }
    }
}