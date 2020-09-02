package hep.dataforge.control.controllers

import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.get
import hep.dataforge.io.Consumer
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.get
import hep.dataforge.meta.string
import hep.dataforge.meta.wrap
import hep.dataforge.names.Name
import hep.dataforge.names.NameToken
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

    private val listeners: Map<NameToken, DeviceListener> = hub.devices.mapValues { (name, device) ->
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

    suspend fun respondMessage(message: DeviceMessage): DeviceMessage = try {
        val targetName = message.target?.toName() ?: Name.EMPTY
        val device = hub[targetName] ?: error("The device with name $targetName not found in $hub")
        DeviceController.respondMessage(device, targetName.toString(), message)
    } catch (ex: Exception) {
        DeviceMessage.fail {
            comment = ex.message
        }
    }

    override suspend fun respond(request: Envelope): Envelope = try {
        val targetName = request.meta[DeviceMessage.TARGET_KEY].string?.toName() ?: Name.EMPTY
        val device = hub[targetName] ?: error("The device with name $targetName not found in $hub")
        if (request.data == null) {
            DeviceController.respondMessage(device, targetName.toString(), DeviceMessage.wrap(request.meta)).wrap()
        } else {
            DeviceController.respond(device, targetName.toString(), request)
        }
    } catch (ex: Exception) {
        DeviceMessage.fail {
            comment = ex.message
        }.wrap()
    }

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }
}