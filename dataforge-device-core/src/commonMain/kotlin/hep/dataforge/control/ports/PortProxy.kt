package hep.dataforge.control.ports

import hep.dataforge.context.Context
import hep.dataforge.context.ContextAware
import hep.dataforge.context.Global
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
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

    /**
     * Ensure that the port is open. If it is already open, does nothing. Otherwise, open a new port.
     */
    public suspend fun open() {
        port()//ignore result
    }

    override suspend fun send(data: ByteArray) {
        port().send(data)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun receiving(): Flow<ByteArray> = channelFlow {
        while (isActive) {
            try {
                //recreate port and Flow on cancel
                port().receiving().collect {
                    send(it)
                }
            } catch (t: Throwable) {
                logger.warn(t){"Port read failed. Reconnecting."}
                //cancel
//                if (t is CancellationException) {
//                    cancel(t)
//                }
            }
        }
    }// port().receiving()

    // open by default
    override fun isOpen(): Boolean = true

    override fun close() {
        actualPort?.close()
        actualPort = null
    }
}