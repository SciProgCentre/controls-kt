package hep.dataforge.control.server

import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.io.*
import hep.dataforge.meta.MetaSerializer
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.Frame
import io.ktor.response.respondText
import kotlinx.io.asBinary
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.json

fun Frame.toEnvelope(): Envelope {
    return data.asBinary().readWith(TaggedEnvelopeFormat)
}

fun Envelope.toFrame(): Frame {
    val data = buildByteArray {
        writeWith(TaggedEnvelopeFormat,this@toFrame)
    }
    return Frame.Binary(false, data)
}

suspend fun ApplicationCall.respondJson(builder: JsonObjectBuilder.() -> Unit) {
    val json = json(builder)
    respondText(json.toString(), contentType = ContentType.Application.Json)
}

@OptIn(UnstableDefault::class)
suspend fun ApplicationCall.respondMessage(message: DeviceMessage) {
    respondText(Json.stringify(MetaSerializer,message.toMeta()), contentType = ContentType.Application.Json)
}

suspend fun ApplicationCall.respondMessage(builder: DeviceMessage.() -> Unit) {
    respondMessage(DeviceMessage(builder))
}

suspend fun ApplicationCall.respondFail(builder: DeviceMessage.() -> Unit) {
    respondMessage(DeviceMessage.fail(null, builder))
}