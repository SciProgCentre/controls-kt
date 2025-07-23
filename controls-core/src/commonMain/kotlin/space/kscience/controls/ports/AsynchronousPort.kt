package space.kscience.controls.ports

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.io.Source
import space.kscience.controls.api.AsynchronousSocket
import space.kscience.controls.api.LifecycleState
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.coroutines.CoroutineContext

/**
 * Raw [ByteArray] port
 */
public interface AsynchronousPort : ContextAware, AsynchronousSocket<ByteArray>

/**
 * Capture [AsynchronousPort] output as kotlinx-io [Source].
 * [scope] controls the consummation.
 * If the scope is canceled, the source stops producing.
 */
public fun AsynchronousPort.receiveAsSource(scope: CoroutineScope): Source = subscribe().consumeAsSource(scope)


/**
 * Common abstraction for [AsynchronousPort] based on [Channel]
 */
public abstract class AbstractAsynchronousPort(
    override val context: Context,
    public val meta: Meta,
    coroutineContext: CoroutineContext = context.coroutineContext,
) : AsynchronousPort {


    protected val scope: CoroutineScope by lazy {
        CoroutineScope(
            coroutineContext +
                    SupervisorJob(coroutineContext[Job]) +
                    CoroutineExceptionHandler { _, throwable -> logger.error(throwable) { "Asynchronous port error: " + throwable.stackTraceToString() } } +
                    CoroutineName(toString())
        )
    }

    private val outgoing = Channel<ByteArray>(meta["outgoing.capacity"].int ?: 100)
    private val incoming = Channel<ByteArray>(meta["incoming.capacity"].int ?: 100)

    /**
     * Internal method to synchronously send data
     */
    protected abstract suspend fun write(data: ByteArray)

    /**
     * Internal method to receive data synchronously
     */
    protected suspend fun receive(data: ByteArray) {
        logger.debug { "$this RECEIVED: ${data.decodeToString()}" }
        incoming.send(data)
    }

    private var sendJob: Job? = null

    protected abstract fun onOpen()

    final override suspend fun start() {
        if (lifecycleState == LifecycleState.STOPPED) {
            sendJob = scope.launch {
                for (data in outgoing) {
                    try {
                        write(data)
                        logger.debug { "${this@AbstractAsynchronousPort} SENT: ${data.decodeToString()}" }
                    } catch (ex: Exception) {
                        if (ex is CancellationException) throw ex
                        logger.error(ex) { "Error while writing data to the port" }
                    }
                }
            }
            onOpen()
        } else {
            logger.warn { "$this already started" }
        }
    }


    /**
     * Send a data packet via the port
     */
    override suspend fun send(data: ByteArray) {
        check(lifecycleState == LifecycleState.STARTED) { "The port is not opened" }
        outgoing.send(data)
    }

    /**
     * Raw discrete of incoming data chunks. The chunks are not guaranteed to be complete phrases.
     * To form phrases, some condition should be used on top of it.
     * For example [stringsDelimitedIncoming] generates phrases with fixed delimiter.
     */
    override fun subscribe(): Flow<ByteArray> = incoming.receiveAsFlow()

    override suspend fun stop() {
        outgoing.close()
        incoming.close()
        sendJob?.cancel()
    }

    override fun toString(): String = meta["name"].string ?: "ChannelPort[${hashCode().toString(16)}]"
}

/**
 * Send UTF-8 encoded string
 */
public suspend fun AsynchronousPort.send(string: String): Unit = send(string.encodeToByteArray())