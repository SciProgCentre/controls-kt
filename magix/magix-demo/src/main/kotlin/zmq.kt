import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.server.startMagixServer
import ru.mipt.npm.magix.zmq.ZmqMagixEndpoint


suspend fun MagixEndpoint<JsonElement>.sendJson(
    origin: String,
    format: String = "json",
    target: String? = null,
    id: String? = null,
    parentId: String? = null,
    user: JsonElement? = null,
    builder: JsonObjectBuilder.() -> Unit
): Unit = broadcast(MagixMessage(format, origin, buildJsonObject(builder), target, id, parentId, user))

suspend fun main(): Unit = coroutineScope {
    val logger = LoggerFactory.getLogger("magix-demo")
    logger.info("Starting magix server")
    val server = startMagixServer(enableRawRSocket = false)
    logger.info("Waiting for server to start")
    delay(2000)

    logger.info("Starting client")
    ZmqMagixEndpoint("tcp://localhost", JsonElement.serializer()).use { client ->
        logger.info("Starting subscription")
        try {
            client.subscribe().onEach {
                println(it.payload)
            }.catch { it.printStackTrace() }.launchIn(this)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }


        var counter = 0
        while (isActive) {
            delay(500)
            logger.info("Sending message number ${counter + 1}")
            client.sendJson("magix-demo") {
                put("message", "Hello world!")
                put("index", counter++)
            }
        }

    }
}