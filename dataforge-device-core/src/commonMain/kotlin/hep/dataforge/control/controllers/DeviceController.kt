package hep.dataforge.control.controllers

import hep.dataforge.control.api.Consumer
import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.controllers.DeviceMessage.Companion.PROPERTY_CHANGED_ACTION
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.MetaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Binary

class DeviceController(
    val device: Device,
    val deviceTarget: String,
    val scope: CoroutineScope = device.scope
) : Responder, Consumer, DeviceListener {

    init {
        device.registerListener(this, this)
    }

    private val outputChannel = Channel<Envelope>(Channel.CONFLATED)

    suspend fun respondMessage(message: DeviceMessage): DeviceMessage {
        return Device.respondMessage(device, deviceTarget, message)
    }

    override suspend fun respond(request: Envelope): Envelope {
        return Device.respond(device, deviceTarget, request)
    }

    override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        if (value == null) return
        scope.launch {
            val change = DeviceMessage.ok {
                this.source = deviceTarget
                type = PROPERTY_CHANGED_ACTION
                data {
                    name = propertyName
                    this.value = value
                }
            }
            val envelope = SimpleEnvelope(change.toMeta(), Binary.EMPTY)

            outputChannel.send(envelope)
        }
    }

    fun output() = outputChannel.consumeAsFlow()

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }

    companion object {

    }
}
