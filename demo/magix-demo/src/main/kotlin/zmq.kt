import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.server.startMagixServer
import ru.mipt.npm.magix.zmq.ZmqMagixEndpoint
import java.awt.Desktop
import java.net.URI


suspend fun MagixEndpoint.sendJson(
    origin: String,
    format: String = "json",
    target: String? = null,
    id: String? = null,
    parentId: String? = null,
    user: JsonElement? = null,
    builder: JsonObjectBuilder.() -> Unit
): Unit = broadcast(MagixMessage(format, buildJsonObject(builder), origin, target, id, parentId, user))

internal const val numberOfMessages = 100

suspend fun main(): Unit = coroutineScope {
    val logger = LoggerFactory.getLogger("magix-demo")
    logger.info("Starting magix server")
    val server = startMagixServer(
        buffer = 10,
        enableRawRSocket = false //Disable rsocket to avoid kotlin 1.5 compatibility issue
    )

    server.apply {
        val host = "localhost"//environment.connectors.first().host
        val port = environment.connectors.first().port
        val uri = URI("http", null, host, port, "/state", null, null)
        Desktop.getDesktop().browse(uri)
    }

    logger.info("Starting client")
    //Create zmq magix endpoint and wait for to finish
    ZmqMagixEndpoint("localhost","tcp").use { client ->
        logger.info("Starting subscription")
        client.subscribe().onEach {
            println(it.payload)
            if (it.payload.jsonObject["index"]?.jsonPrimitive?.int == numberOfMessages) {
                logger.info("Index $numberOfMessages reached. Terminating")
                cancel()
            }
        }.catch { it.printStackTrace() }.launchIn(this)


        var counter = 0
        while (isActive) {
            delay(500)
            val index = (counter++).toString()
            logger.info("Sending message number $index")
            client.sendJson("magix-demo", id = index) {
                put("message", "Hello world!")
                put("index", index)
            }
        }

    }
}