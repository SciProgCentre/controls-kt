package hep.dataforge.control.client

import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.respondMessage
import hep.dataforge.control.sse.SseEvent
import hep.dataforge.control.sse.readSseFlow
import hep.dataforge.meta.toJson
import hep.dataforge.meta.toMeta
import hep.dataforge.meta.wrap
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext


private suspend fun HttpClient.readSse(address: String, block: suspend (SseEvent) -> Unit): Job = launch {
    get<HttpStatement>(address).execute { response: HttpResponse ->
        // Response is not downloaded here.
        val channel = response.receive<ByteReadChannel>()
        val flow = channel.readSseFlow()
        flow.collect(block)
    }
}

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
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1)
 */
public class MagixClient(
    private val manager: DeviceManager,
    private val postUrl: Url,
    private val sseUrl: Url
    //private val inbox: Flow<JsonObject>
): CoroutineScope {

    override val coroutineContext: CoroutineContext = manager.context.coroutineContext + Job(manager.context.coroutineContext[Job])

    private val client = HttpClient()

    protected fun generateId(message: DeviceMessage, requestId: String?): String = if(requestId != null){
        "$requestId.response"
    } else{
        "df[${message.hashCode()}"
    }

    private fun send(json: JsonObject) {
        launch {
            client.post<Unit>(postUrl) {
                this.contentType(ContentType.Application.Json)
                body = json.toString()
            }
        }
    }

    private fun wrapMessage(message: DeviceMessage, requestId: String? = null): JsonObject = buildJsonObject {
        put("id", generateId(message, requestId))
        if (requestId != null) {
            put("parentId", requestId)
        }
        put("target", "magix")
        put("origin", "df")
        put("payload", message.config.toJson())
    }


    private val listenJob = launch {
        manager.controller.messageOutput().collect { message ->
            val json = wrapMessage(message)
            send(json)
        }
    }

    private val respondJob = launch {
        client.readSse(sseUrl.toString()){
            val json = Json.parseToJsonElement(it.data) as JsonObject

            val requestId = json["id"]?.jsonPrimitive?.content
            val payload = json["payload"]?.jsonObject
            //TODO analyze action

            if(payload != null){
                val meta = payload.toMeta()
                val request = DeviceMessage.wrap(meta)
                val response = manager.respondMessage(request)
                send(wrapMessage(response,requestId))
            } else {
                TODO("process heartbeat and other system messages")
            }
        }
    }
}



