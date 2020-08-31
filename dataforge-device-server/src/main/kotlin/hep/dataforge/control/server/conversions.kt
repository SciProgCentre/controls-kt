package hep.dataforge.control.server

import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.io.*
import hep.dataforge.meta.MetaSerializer
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.Frame
import io.ktor.response.respondText
import kotlinx.io.asBinary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject


internal fun Frame.toEnvelope(): Envelope {
    return data.asBinary().readWith(TaggedEnvelopeFormat)
}

internal fun Envelope.toFrame(): Frame {
    val data = buildByteArray {
        writeWith(TaggedEnvelopeFormat, this@toFrame)
    }
    return Frame.Binary(false, data)
}

internal suspend fun ApplicationCall.respondJson(builder: JsonObjectBuilder.() -> Unit) {
    val json = buildJsonObject(builder)
    respondText(json.toString(), contentType = ContentType.Application.Json)
}


public suspend fun ApplicationCall.respondMessage(message: DeviceMessage) {
    respondText(Json.encodeToString(MetaSerializer, message.toMeta()), contentType = ContentType.Application.Json)
}

public suspend fun ApplicationCall.respondMessage(builder: DeviceMessage.() -> Unit) {
    respondMessage(DeviceMessage(builder))
}

public suspend fun ApplicationCall.respondFail(builder: DeviceMessage.() -> Unit) {
    respondMessage(DeviceMessage.fail(null, builder))
}