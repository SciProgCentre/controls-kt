package ru.mipt.npm.magix.server

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.rsocket.kotlin.core.RSocketServer
import io.rsocket.kotlin.transport.ktor.serverTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_HTTP_PORT
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_RAW_PORT

/**
 *
 */
public fun CoroutineScope.rawMagixServerSocket(
    magixFlow: MutableSharedFlow<GenericMagixMessage>,
    rawSocketPort: Int = DEFAULT_MAGIX_RAW_PORT
): Job {
    val tcpTransport = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().serverTransport(port = rawSocketPort)
    val rSocketJob = RSocketServer().bind(tcpTransport, magixAcceptor(magixFlow))
    coroutineContext[Job]?.invokeOnCompletion{
        rSocketJob.cancel()
    }
    return rSocketJob;
}

public fun CoroutineScope.startMagixServer(
    port: Int = DEFAULT_MAGIX_HTTP_PORT,
    rawSocketPort: Int = DEFAULT_MAGIX_RAW_PORT,
    buffer: Int = 100,
): ApplicationEngine {

    val magixFlow = MutableSharedFlow<GenericMagixMessage>(
        buffer,
        extraBufferCapacity = buffer
    )

    rawMagixServerSocket(magixFlow, rawSocketPort)

    return embeddedServer(CIO, port = port) {
        magixModule(magixFlow)
    }
}