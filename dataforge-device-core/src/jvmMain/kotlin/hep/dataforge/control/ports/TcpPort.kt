package hep.dataforge.control.ports

import kotlinx.coroutines.*
import mu.KLogger
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.coroutines.coroutineContext

internal fun ByteBuffer.readArray(limit: Int = limit()): ByteArray {
    rewind()
    val response = ByteArray(limit)
    get(response)
    rewind()
    return response
}

class TcpPort private constructor(
    scope: CoroutineScope,
    val host: String,
    val port: Int
) : Port(scope), AutoCloseable {

    override val logger: KLogger = KotlinLogging.logger("port[tcp:$host:$port]")

    private val futureChannel: Deferred<SocketChannel> = this.scope.async(Dispatchers.IO) {
        SocketChannel.open(InetSocketAddress(host, port)).apply {
            configureBlocking(false)
        }
    }

    /**
     * A handler to await port connection
     */
    val startJob: Job get() = futureChannel

    private val listenerJob = this.scope.launch {
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
                logger.error("Channel read error", ex)
                delay(1000)
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        futureChannel.await().write(ByteBuffer.wrap(data))
    }

    override fun close() {
        listenerJob.cancel()
        futureChannel.cancel()
        super.close()
    }

    companion object{
        suspend fun open(host: String, port: Int): TcpPort{
            val scope = CoroutineScope(SupervisorJob(coroutineContext[Job]))
            return TcpPort(scope, host, port)
        }
    }
}