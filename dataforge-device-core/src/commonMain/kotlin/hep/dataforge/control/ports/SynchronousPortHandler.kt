package hep.dataforge.control.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A port handler for synchronous (request-response) communication with a port. Only one request could be active at a time (others are suspended.
 * The handler does not guarantee exclusive access to the port so the user mush ensure that no other controller handles port at the moment.
 *
 */
class SynchronousPortHandler(val port: Port) {
    private val mutex = Mutex()

    /**
     * Send a single message and wait for the flow of respond messages.
     */
    suspend fun <R> respond(data: ByteArray, transform: suspend Flow<ByteArray>.() -> R): R {
        return mutex.withLock {
            port.send(data)
            transform(port.incoming())
        }
    }
}

/**
 * Send request and read incoming data blocks until the delimiter is encountered
 */
suspend fun SynchronousPortHandler.respondWithDelimiter(data: ByteArray, delimiter: ByteArray): ByteArray {
    return respond(data) {
        withDelimiter(delimiter).first()
    }
}