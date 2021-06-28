package ru.mipt.npm.magix.server

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.rsocket.kotlin.core.RSocketServer
import io.rsocket.kotlin.transport.ktor.serverTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.zeromq.SocketType
import org.zeromq.ZContext
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_HTTP_PORT
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_RAW_PORT
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_ZMQ_PUB_PORT
import ru.mipt.npm.magix.api.MagixEndpoint.Companion.DEFAULT_MAGIX_ZMQ_PULL_PORT

/**
 * Raw TCP magix server
 */
public fun CoroutineScope.rawMagixServerSocket(
    magixFlow: MutableSharedFlow<GenericMagixMessage>,
    rawSocketPort: Int = DEFAULT_MAGIX_RAW_PORT
): Job {
    val tcpTransport = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().serverTransport(port = rawSocketPort)
    val rSocketJob = RSocketServer().bind(tcpTransport, magixAcceptor(magixFlow))
    coroutineContext[Job]?.invokeOnCompletion {
        rSocketJob.cancel()
    }
    return rSocketJob;
}

public fun CoroutineScope.zmqMagixServerSocket(
    magixFlow: MutableSharedFlow<GenericMagixMessage>,
    localHost: String = "tcp://*",
    zmqPubSocketPort: Int = DEFAULT_MAGIX_ZMQ_PUB_PORT,
    zmqPullSocketPort: Int = DEFAULT_MAGIX_ZMQ_PULL_PORT,
): Job = launch {
    ZContext().use { context ->
        //launch publishing job
        val pubSocket = context.createSocket(SocketType.XPUB)
        pubSocket.bind("$localHost:$zmqPubSocketPort")
        magixFlow.onEach { message ->
            pubSocket.send(MagixEndpoint.magixJson.encodeToString(genericMessageSerializer, message))
        }.launchIn(this)

        //launch pulling job
        val pullSocket = context.createSocket(SocketType.PULL)
        pubSocket.bind("$localHost:$zmqPullSocketPort")
        launch(Dispatchers.IO) {
            while (isActive) {
                //This is a blocking call.
                val string = pullSocket.recvStr()
                val message = MagixEndpoint.magixJson.decodeFromString(genericMessageSerializer, string)
                magixFlow.emit(message)
            }
        }
    }
}

/**
 * A combined RSocket/TCP server
 */
public fun CoroutineScope.startMagixServer(
    port: Int = DEFAULT_MAGIX_HTTP_PORT,
    buffer: Int = 100,
): ApplicationEngine {

    val magixFlow = MutableSharedFlow<GenericMagixMessage>(
        buffer,
        extraBufferCapacity = buffer
    )

    //start tcpRSocket server
    rawMagixServerSocket(magixFlow)
    zmqMagixServerSocket(magixFlow)

    return embeddedServer(CIO, port = port) {
        magixModule(magixFlow)
    }
}