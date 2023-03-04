package space.kscince.magix.zmq

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFlowPlugin
import space.kscience.magix.api.MagixMessage


public class ZmqMagixFlowPlugin(
    public val localHost: String = "tcp://*",
    public val zmqPubSocketPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PUB_PORT,
    public val zmqPullSocketPort: Int = MagixEndpoint.DEFAULT_MAGIX_ZMQ_PULL_PORT,
) : MagixFlowPlugin {
    override fun start(scope: CoroutineScope, magixFlow: MutableSharedFlow<MagixMessage>): Job =
        scope.launch(Dispatchers.IO) {
            val logger = LoggerFactory.getLogger("magix-server-zmq")

            ZContext().use { context ->
                //launch publishing job
                val pubSocket = context.createSocket(SocketType.PUB)
                pubSocket.bind("$localHost:$zmqPubSocketPort")
                magixFlow.onEach { message ->
                    val string = MagixEndpoint.magixJson.encodeToString(message)
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
                        val message = MagixEndpoint.magixJson.decodeFromString<MagixMessage>(string)
                        magixFlow.emit(message)
                    }
                }
            }
        }


}