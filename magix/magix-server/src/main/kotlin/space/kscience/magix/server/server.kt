package space.kscience.magix.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import space.kscience.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_HTTP_PORT
import space.kscience.magix.api.MagixFlowPlugin
import space.kscience.magix.api.MagixMessage


/**
 * A combined RSocket/TCP/ZMQ server
 */
public fun CoroutineScope.startMagixServer(
    vararg plugins: MagixFlowPlugin,
    port: Int = DEFAULT_MAGIX_HTTP_PORT,
    buffer: Int = 1000,
): ApplicationEngine {

    val magixFlow = MutableSharedFlow<MagixMessage>(
        replay = buffer,
        extraBufferCapacity = buffer,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    plugins.forEach {
        it.start(this, magixFlow)
    }

    return embeddedServer(CIO, host = "localhost", port = port, module = { magixModule(magixFlow) }).apply {
        start()
    }
}