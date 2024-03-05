package space.kscience.controls.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A generic bidirectional sender/receiver object
 */
public interface Socket<T> : AutoCloseable {
    /**
     * Send an object to the socket
     */
    public suspend fun send(data: T)

    /**
     * Flow of objects received from socket
     */
    public fun receiving(): Flow<T>
    public fun isOpen(): Boolean
}

/**
 * Connect an input to this socket using designated [scope] for it and return a handler [Job].
 * Multiple inputs could be connected to the same [Socket].
 */
public fun <T> Socket<T>.connectInput(scope: CoroutineScope, flow: Flow<T>): Job = scope.launch {
    flow.collect { send(it) }
}


