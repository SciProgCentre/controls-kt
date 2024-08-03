package space.kscience.controls.api

import kotlinx.coroutines.flow.Flow

/**
 * A generic bidirectional asynchronous sender/receiver object
 */
public interface AsynchronousSocket<T> : WithLifeCycle {
    /**
     * Send an object to the socket
     */
    public suspend fun send(data: T)

    /**
     * Flow of objects received from socket
     */
    public fun subscribe(): Flow<T>
}

/**
 * Connect an input to this socket.
 * Multiple inputs could be connected to the same [AsynchronousSocket].
 *
 * This method suspends indefinitely, so it should be started in a separate coroutine.
 */
public suspend fun <T> AsynchronousSocket<T>.sendFlow(flow: Flow<T>) {
    flow.collect { send(it) }
}


