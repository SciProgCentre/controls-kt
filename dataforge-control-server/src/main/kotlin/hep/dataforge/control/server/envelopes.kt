package hep.dataforge.control.server

import hep.dataforge.control.controlers.DeviceMessage
import hep.dataforge.io.Envelope
import io.ktor.application.ApplicationCall
import io.ktor.http.cio.websocket.Frame
import io.ktor.response.ApplicationResponse

fun Frame.toEnvelope(): Envelope {
    TODO()
}

fun Envelope.toFrame(): Frame {
    TODO()
}

suspend fun ApplicationCall.respondMessage(message: DeviceMessage) {
    TODO()
}

suspend fun ApplicationCall.respondMessage(builder: DeviceMessage.() -> Unit) {
    respondMessage(DeviceMessage(builder))
}

suspend fun ApplicationCall.respondFail(builder: DeviceMessage.() -> Unit) {
    respondMessage(DeviceMessage.fail(null, builder))
}