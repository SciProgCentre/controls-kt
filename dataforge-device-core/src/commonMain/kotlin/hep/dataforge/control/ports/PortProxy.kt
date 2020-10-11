package hep.dataforge.control.ports

import hep.dataforge.context.Context
import hep.dataforge.context.ContextAware
import hep.dataforge.context.Global
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A port that could be closed multiple times and opens automatically on request
 */
public class PortProxy(override val context: Context = Global, public val factory: suspend () -> Port) : Port, ContextAware {

    private var actualPort: Port? = null
    private val mutex: Mutex = Mutex()

    private suspend fun port(): Port {
        return mutex.withLock {
            if (actualPort?.isOpen() == true) {
                actualPort!!
            } else {
                factory().also {
                    actualPort = it
                }
            }
        }
    }

    override suspend fun send(data: ByteArray) {
        port().send(data)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun receiving(): Flow<ByteArray> = flow {
        while (true) {
            try {
                //recreate port and Flow on connection problems
                port().receiving().collect {
                    emit(it)
                }
            } catch (t: Throwable) {
                logger.warn(t){"Port read failed. Reconnecting."}
                mutex.withLock {
                    actualPort?.close()
                    actualPort = null
                }
            }
        }
    }

    // open by default
    override fun isOpen(): Boolean = true

    override fun close() {
        context.launch {
            mutex.withLock {
                actualPort?.close()
                actualPort = null
            }
        }
    }
}