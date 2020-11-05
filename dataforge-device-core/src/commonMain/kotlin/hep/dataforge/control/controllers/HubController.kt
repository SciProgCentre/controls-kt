package hep.dataforge.control.controllers

import hep.dataforge.control.api.DeviceHub
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.get
import hep.dataforge.io.Consumer
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.meta.DFExperimental
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.get
import hep.dataforge.meta.string
import hep.dataforge.names.Name
import hep.dataforge.names.NameToken
import hep.dataforge.names.toName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


@OptIn(DFExperimental::class)
public class HubController(
    public val hub: DeviceHub,
    public val scope: CoroutineScope,
) : Consumer, Responder {

    private val messageOutbox = Channel<DeviceMessage>(Channel.CONFLATED)

    private val envelopeOutbox = Channel<Envelope>(Channel.CONFLATED)

    public fun messageOutput(): Flow<DeviceMessage> = messageOutbox.consumeAsFlow()

    public fun envelopeOutput(): Flow<Envelope> = envelopeOutbox.consumeAsFlow()

    private val packJob = scope.launch {
        while (isActive) {
            val message = messageOutbox.receive()
            envelopeOutbox.send(message.toEnvelope())
        }
    }

    private val listeners: Map<NameToken, DeviceListener> = hub.devices.mapValues { (name, device) ->
        object : DeviceListener {
            override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
                if (value == null) return
                scope.launch {
                    val change = DeviceMessage(
                        sourceName = name.toString(),
                        action = DeviceMessage.PROPERTY_CHANGED_ACTION,
                        key = propertyName,
                        value = value
                    )

                    messageOutbox.send(change)
                }
            }
        }.also {
            device.registerListener(it)
        }
    }

    public suspend fun respondMessage(message: DeviceMessage): DeviceMessage = try {
        val targetName = message.targetName?.toName() ?: Name.EMPTY
        val device = hub[targetName] ?: error("The device with name $targetName not found in $hub")
        DeviceController.respondMessage(device, targetName.toString(), message)
    } catch (ex: Exception) {
        DeviceMessage.fail(ex, message.action).respondsTo(message)
    }

    override suspend fun respond(request: Envelope): Envelope = try {
        val targetName = request.meta[DeviceMessage.TARGET_KEY].string?.toName() ?: Name.EMPTY
        val device = hub[targetName] ?: error("The device with name $targetName not found in $hub")
        if (request.data == null) {
            DeviceController.respondMessage(device, targetName.toString(), DeviceMessage.fromMeta(request.meta)).toEnvelope()
        } else {
            DeviceController.respond(device, targetName.toString(), request)
        }
    } catch (ex: Exception) {
        DeviceMessage.fail(ex).toEnvelope()
    }

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }
}