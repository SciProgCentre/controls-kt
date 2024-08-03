package space.kscience.controls.ports

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.WithLifeCycle
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware

/**
 * A port handler for synchronous (request-response) communication with a port.
 * Only one request could be active at a time (others are suspended).
 */
public interface SynchronousPort : ContextAware, WithLifeCycle {

    /**
     * Send a single message and wait for the flow of response chunks.
     * The consumer is responsible for calling a terminal operation on the flow.
     */
    public suspend fun <R> respond(
        request: ByteArray,
        transform: suspend Flow<ByteArray>.() -> R,
    ): R

    /**
     * Synchronously read fixed size response to a given [request]. Discard additional response bytes.
     */
    public suspend fun respondFixedMessageSize(
        request: ByteArray,
        responseSize: Int,
    ): ByteArray = respond(request) {
        val buffer = Buffer()
        takeWhile {
            buffer.size < responseSize
        }.collect {
            buffer.write(it)
        }
        buffer.readByteArray(responseSize)
    }
}

/**
 * Read response to a given message using [Source] abstraction
 */
public suspend fun <R> SynchronousPort.respondAsSource(
    request: ByteArray,
    transform: suspend Source.() -> R,
): R = respond(request) {
    //suspend until the response is fully read
    coroutineScope {
        val buffer = Buffer()
        val collectJob = onEach { buffer.write(it) }.launchIn(this)
        val res = transform(buffer)
        //cancel collection when the result is achieved
        collectJob.cancel()
        res
    }
}

private class SynchronousOverAsynchronousPort(
    val port: AsynchronousPort,
    val mutex: Mutex,
) : SynchronousPort {

    override val context: Context get() = port.context

    override suspend fun start() {
        if (port.lifecycleState == LifecycleState.STOPPED) port.start()
    }

    override val lifecycleState: LifecycleState get() = port.lifecycleState

    override suspend fun stop() {
        if (port.lifecycleState == LifecycleState.STARTED) port.stop()
    }

    override suspend fun <R> respond(
        request: ByteArray,
        transform: suspend Flow<ByteArray>.() -> R,
    ): R = mutex.withLock {
        port.send(request)
        transform(port.subscribe())
    }
}


/**
 * Provide a synchronous wrapper for an asynchronous port.
 * Optionally provide external [mutex] for operation synchronization.
 *
 * If the [AsynchronousPort] is called directly, it could violate [SynchronousPort] contract
 * of only one request running simultaneously.
 */
public fun AsynchronousPort.asSynchronousPort(mutex: Mutex = Mutex()): SynchronousPort =
    SynchronousOverAsynchronousPort(this, mutex)

/**
 * Send request and read incoming data blocks until the delimiter is encountered
 */
public suspend fun SynchronousPort.respondWithDelimiter(
    data: ByteArray,
    delimiter: ByteArray,
): ByteArray = respond(data) {
    withDelimiter(delimiter).first()
}

public suspend fun SynchronousPort.respondStringWithDelimiter(
    data: String,
    delimiter: String,
): String = respond(data.encodeToByteArray()) {
    withStringDelimiter(delimiter).first()
}