package hep.dataforge.control.ports

import kotlinx.coroutines.*
import mu.KLogger
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("UNCHECKED_CAST")
private fun <T> asyncIOHandler(): CompletionHandler<T, CancellableContinuation<T>> =
    object : CompletionHandler<T, CancellableContinuation<T>> {
        override fun completed(result: T, cont: CancellableContinuation<T>) {
            cont.resume(result)
        }

        override fun failed(ex: Throwable, cont: CancellableContinuation<T>) {
            // just return if already cancelled and got an expected exception for that case
            if (ex is AsynchronousCloseException && cont.isCancelled) return
            cont.resumeWithException(ex)
        }
    }

suspend fun AsynchronousSocketChannel.readSuspended(
    buf: ByteBuffer
) = suspendCancellableCoroutine<Int> { cont ->
    read(buf, cont, asyncIOHandler<Int>())
    cont.invokeOnCancellation {
        try {
            close()
        } catch (ex: Throwable) {
            // Specification says that it is Ok to call it any time, but reality is different,
            // so we have just to ignore exception
        }
    }
}


private fun ByteBuffer.toArray(limit: Int = limit()): ByteArray{
    rewind()
    val response = ByteArray(limit)
    get(response)
    rewind()
    return response
}


class TcpPort(
    parentScope: CoroutineScope,
    val ip: String,
    val port: Int
) : Port() {

    override val logger: KLogger = KotlinLogging.logger("[tcp]$ip:$port")

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r).apply {
            name = "[tcp]$ip:$port"
            priority = Thread.MAX_PRIORITY
        }
    }
    override val coroutineContext: CoroutineContext = parentScope.coroutineContext + executor.asCoroutineDispatcher()

    private var socket: AsynchronousSocketChannel = openSocket()

    private fun openSocket()= AsynchronousSocketChannel.open().bind(InetSocketAddress(ip, port))

    private val listenerJob = launch {
        val buffer = ByteBuffer.allocate(1024)
        while (isActive) {
            try {
                val num = socket.readSuspended(buffer)
                if (num > 0) {
                    receive(buffer.toArray(num))
                }
            } catch (ex: Exception) {
                logger.error("Channel read error", ex)
                delay(100)
                logger.info("Reconnecting")
                socket = openSocket()
            }
        }
    }

    override fun sendInternal(data: ByteArray) {
        if (!socket.isOpen) socket = openSocket()
        socket.write(ByteBuffer.wrap(data))
    }

}