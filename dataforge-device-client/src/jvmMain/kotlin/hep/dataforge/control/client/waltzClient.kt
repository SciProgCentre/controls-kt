package hep.dataforge.control.client

import hep.dataforge.control.api.getDevice
import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.MessageController
import hep.dataforge.meta.Meta
import hep.dataforge.meta.toJson
import hep.dataforge.meta.toMeta
import hep.dataforge.meta.wrap
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
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
 * Convert a [DeviceMessage] to [Waltz format](https://github.com/waltz-controls/rfc/tree/master/1)
 */
fun DeviceMessage.toWaltz(id: String, parentId: String): JsonObject = json {
    "id" to id
    "parentId" to parentId
    "target" to "magix"
    "origin" to "df"
    "payload" to config.toJson()
}

fun DeviceMessage.fromWaltz(json: JsonObject): DeviceMessage =
    DeviceMessage.wrap(json["payload"]?.jsonObject?.toMeta() ?: Meta.EMPTY)

fun DeviceManager.startWaltzClient(
    waltzUrl: Url,
    deviceNames: Collection<String> = devices.keys.map { it.toString() }
): Job {

    val controllers = deviceNames.map { name ->
        val device = getDevice(name)
        MessageController(device, name, context)
    }

    val client =  HttpClient()

    val outputFlow = controllers.asFlow().flatMapMerge {
        it.output()
    }.filter { it.data == null }.map { DeviceMessage.wrap(it.meta) }

    return context.launch {
        outputFlow.collect { message ->
            client.post(waltzUrl){
                this.contentType(ContentType.Application.Json)
                body = message.config.toJson().toString()
            }
        }
    }
}