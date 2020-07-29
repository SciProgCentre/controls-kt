package hep.dataforge.control.client

import hep.dataforge.control.api.respondMessage
import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.meta.toJson
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.json

/*
{
  "id":"string|number[optional, but desired]",
  "parentId": "string|number[optional]",
  "target":"string[optional]",
  "origin":"string[required]",
  "user":"string[optional]",
  "action":"string[optional, default='heartbeat']",
  "payload":"object[optional]"
}
 */

/**
 * A stateful unique id generator
 */
interface IdGenerator{
    operator fun invoke(message: DeviceMessage): String
}

object MagixClient {
    /**
     * Convert a [DeviceMessage] to [Waltz format](https://github.com/waltz-controls/rfc/tree/master/1)
     */
    fun DeviceMessage.toWaltz(id: String, parentId: String? = null): JsonObject = json {
        "id" to id
        if (parentId != null) {
            "parentId" to parentId
        }
        "target" to "magix"
        "origin" to "df"
        "payload" to config.toJson()
    }

    fun buildCallback(url: Url, idGenerator: IdGenerator): suspend (DeviceMessage) -> Unit {
        val client = HttpClient()
        return { message ->
            client.post(url) {
                val messageId = idGenerator(message)
                val waltzMessage = message.toWaltz(messageId)
                this.contentType(ContentType.Application.Json)
                body = waltzMessage.toString()
            }
        }
    }

}

/**
 * Event loop for magix input and output flows
 */
fun DeviceManager.startMagix(
    inbox: Flow<DeviceMessage>, // Inbox flow like SSE
    outbox: suspend (DeviceMessage) -> Unit // outbox callback
): Job = context.launch {
    launch {
        controller.messageOutput().collect { message ->
            outbox.invoke(message)
        }
    }
    launch {
        inbox.collect { message ->
            val response = respondMessage(message)
            outbox.invoke(response)
        }
    }
}
