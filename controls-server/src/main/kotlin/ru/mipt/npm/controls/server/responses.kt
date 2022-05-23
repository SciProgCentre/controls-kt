package ru.mipt.npm.controls.server

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.magix.api.MagixEndpoint


//internal fun Frame.toEnvelope(): Envelope {
//    return data.asBinary().readWith(TaggedEnvelopeFormat)
//}
//
//internal fun Envelope.toFrame(): Frame {
//    val data = buildByteArray {
//        writeWith(TaggedEnvelopeFormat, this@toFrame)
//    }
//    return Frame.Binary(false, data)
//}

internal suspend fun ApplicationCall.respondJson(builder: JsonObjectBuilder.() -> Unit) {
    val json = buildJsonObject(builder)
    respondText(json.toString(), contentType = ContentType.Application.Json)
}

internal suspend fun ApplicationCall.respondMessage(message: DeviceMessage): Unit = respondText(
    MagixEndpoint.magixJson.encodeToString(DeviceMessage.serializer(), message),
    contentType = ContentType.Application.Json
)