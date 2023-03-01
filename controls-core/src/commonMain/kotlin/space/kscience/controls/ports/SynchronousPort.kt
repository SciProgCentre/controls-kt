package space.kscience.controls.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A port handler for synchronous (request-response) communication with a port. Only one request could be active at a time (others are suspended.
 * The handler does not guarantee exclusive access to the port so the user mush ensure that no other controller handles port at the moment.
 */
public class SynchronousPort(public val port: Port, private val mutex: Mutex) : Port by port {
    /**
     * Send a single message and wait for the flow of respond messages.
     */
    public suspend fun <R> respond(data: ByteArray, transform: suspend Flow<ByteArray>.() -> R): R = mutex.withLock {
        port.send(data)
        transform(port.receiving())
    }
}

/**
 * Provide a synchronous wrapper for a port
 */
public fun Port.synchronous(mutex: Mutex = Mutex()): SynchronousPort = SynchronousPort(this, mutex)

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