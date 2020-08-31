package hep.dataforge.control.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PortProxy(val factory: suspend () -> Port) : Port {

    private var actualPort: Port? = null
    private val mutex = Mutex()

    suspend fun port(): Port{
        return mutex.withLock {
            if(actualPort?.isOpen() == true){
                actualPort!!
            } else {
                factory().also{
                    actualPort = it
                }
            }
        }
    }

    override suspend fun send(data: ByteArray) {
        port().send(data)
    }

    override suspend fun receiving(): Flow<ByteArray> = port().receiving()

    // open by default
    override fun isOpen(): Boolean = true

    override fun close() {
        actualPort?.close()
    }
}