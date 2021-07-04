package ru.mipt.npm.magix.server

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import ru.mipt.npm.magix.api.MagixEndpoint

public fun CoroutineScope.launchMagixServerZmqSocket(
    magixFlow: MutableSharedFlow<GenericMagixMessage>,
    localHost: String = "tcp://*",
    zmqPubSocketPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PUB_PORT,
    zmqPullSocketPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PULL_PORT,
): Job = launch(Dispatchers.IO) {
    val logger = LoggerFactory.getLogger("magix-server-zmq")

    ZContext().use { context ->
        //launch publishing job
        val pubSocket = context.createSocket(SocketType.PUB)
        pubSocket.bind("$localHost:$zmqPubSocketPort")
        magixFlow.onEach { message ->
            val string = MagixEndpoint.magixJson.encodeToString(genericMessageSerializer, message)
            pubSocket.send(string)
            logger.debug("Published: $string")
        }.launchIn(this)

        //launch pulling job
        val pullSocket = context.createSocket(SocketType.PULL)
        pullSocket.bind("$localHost:$zmqPullSocketPort")
        pullSocket.receiveTimeOut = 500
        //suspending loop while pulling is active
        while (isActive) {
            val string: String? = pullSocket.recvStr()
            if (string != null) {
                logger.debug("Received: $string")
                val message = MagixEndpoint.magixJson.decodeFromString(genericMessageSerializer, string)
                magixFlow.emit(message)
            }
        }
    }
}

