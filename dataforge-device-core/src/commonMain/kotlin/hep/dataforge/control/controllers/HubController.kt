package hep.dataforge.control.controllers

import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.get
import hep.dataforge.control.api.respondMessage
import hep.dataforge.io.Consumer
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import hep.dataforge.names.Name
import hep.dataforge.names.toName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HubController(
    val hub: DeviceHub,
    val scope: CoroutineScope
) : Consumer, Responder {

    private val messageOutbox = Channel<DeviceMessage>(Channel.CONFLATED)

    private val envelopeOutbox = Channel<Envelope>(Channel.CONFLATED)

    fun messageOutput() = messageOutbox.consumeAsFlow()

    fun envelopeOutput() = envelopeOutbox.consumeAsFlow()

    private val packJob = scope.launch {
        while (isActive) {
            val message = messageOutbox.receive()
            envelopeOutbox.send(message.wrap())
        }
    }

    private val listeners: Map<Name, DeviceListener> = hub.devices.mapValues { (name, device) ->
        object : DeviceListener {
            override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
                if (value == null) return
                scope.launch {
                    val change = DeviceMessage.ok {
                        source = name.toString()
                        type = DeviceMessage.PROPERTY_CHANGED_ACTION
                        data {
                            this.name = propertyName
                            this.value = value
                        }
                    }

                    messageOutbox.send(change)
                }
            }
        }.also {
            device.registerListener(it)
        }
    }

    suspend fun respondMessage(message: DeviceMessage): DeviceMessage {
        return try {
            val targetName = message.target?.toName() ?: Name.EMPTY
            val device = hub[targetName]
            device.respondMessage(message).apply {
                target = message.source
                source = targetName.toString()
            }
        } catch (ex: Exception) {
            DeviceMessage.fail {
                comment = ex.message
            }
        }
    }

    override suspend fun respond(request: Envelope): Envelope {
        val targetName = request.meta[DeviceMessage.TARGET_KEY].string?.toName() ?: Name.EMPTY
        return try {
            val device = hub[targetName]
            if (request.data == null) {
                respondMessage(DeviceMessage.wrap(request.meta)).wrap()
            } else {
                val response = device.respond(request)
                return SimpleEnvelope(response.meta.edit {
                    DeviceMessage.TARGET_KEY put request.meta[DeviceMessage.SOURCE_KEY].string
                    DeviceMessage.SOURCE_KEY put targetName.toString()
                }, response.data)
            }
        } catch (ex: Exception) {
            DeviceMessage.fail {
                comment = ex.message
            }.wrap()
        }
    }

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }
}