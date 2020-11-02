package hep.dataforge.magix.server

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

public const val DEFAULT_MAGIX_SERVER_PORT: Int = 7777
public const val DEFAULT_MAGIX_RAW_PORT: Int = 7778

@OptIn(KtorExperimentalAPI::class)
public fun startMagixServer(port: Int = DEFAULT_MAGIX_SERVER_PORT, host: String = "0.0.0.0", buffer: Int = 100): ApplicationEngine {

    val magixFlow = MutableSharedFlow<GenericMagixMessage>(
        buffer,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    //TODO add raw sockets server from https://github.com/rsocket/rsocket-kotlin/blob/master/examples/multiplatform-chat/src/serverJvmMain/kotlin/App.kt#L102

    //    val tcpTransport = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().serverTransport(port = 8000)
    //    rSocketServer.bind(tcpTransport, acceptor)

    return embeddedServer(CIO, port = port, host = host){
        magixModule(magixFlow)
    }
}