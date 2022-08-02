package space.kscience.controls.ports

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import space.kscience.controls.api.Socket
import space.kscience.dataforge.context.*
import kotlin.coroutines.CoroutineContext

public interface Port : ContextAware, Socket<ByteArray>

public typealias PortFactory = Factory<Port>

public abstract class AbstractPort(
    override val context: Context,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : Port {

    protected val scope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))

    private val outgoing = Channel<ByteArray>(100)
    private val incoming = Channel<ByteArray>(Channel.CONFLATED)

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            close()
        }
    }

    /**
     * Internal method to synchronously send data
     */
    protected abstract suspend fun write(data: ByteArray)

    /**
     * Internal method to receive data synchronously
     */
    protected fun receive(data: ByteArray) {
        scope.launch {
            logger.debug { "${this@AbstractPort} RECEIVED: ${data.decodeToString()}" }
            incoming.send(data)
        }
    }

    private val sendJob = scope.launch {
        for (data in outgoing) {
            try {
                write(data)
                logger.debug { "${this@AbstractPort} SENT: ${data.decodeToString()}" }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                logger.error(ex) { "Error while writing data to the port" }
            }
        }
    }

    /**
     * Send a data packet via the port
     */
    override suspend fun send(data: ByteArray) {
        outgoing.send(data)
    }

    /**
     * Raw flow of incoming data chunks. The chunks are not guaranteed to be complete phrases.
     * In order to form phrases some condition should used on top of it.
     * For example [delimitedIncoming] generates phrases with fixed delimiter.
     */
    override fun receiving(): Flow<ByteArray> {
        return incoming.receiveAsFlow()
    }

    override fun close() {
        outgoing.close()
        incoming.close()
        sendJob.cancel()
        scope.cancel()
    }

    override fun isOpen(): Boolean = scope.isActive
}

/**
 * Send UTF-8 encoded string
 */
public suspend fun Port.send(string: String): Unit = send(string.encodeToByteArray())