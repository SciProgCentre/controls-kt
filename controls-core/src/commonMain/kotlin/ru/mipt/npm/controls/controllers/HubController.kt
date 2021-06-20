package space.kscience.dataforge.control.controllers

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import space.kscience.dataforge.control.api.DeviceHub
import space.kscience.dataforge.control.api.getOrNull
import space.kscience.dataforge.control.messages.DeviceMessage
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.toName


@OptIn(DFExperimental::class)
public class HubController(
    public val hub: DeviceHub,
)  {

    private val messageOutbox = Channel<DeviceMessage>(Channel.CONFLATED)

//    private val envelopeOutbox = Channel<Envelope>(Channel.CONFLATED)

    public fun messageOutput(): Flow<DeviceMessage> = messageOutbox.consumeAsFlow()

//    public fun envelopeOutput(): Flow<Envelope> = envelopeOutbox.consumeAsFlow()

//    private val packJob = scope.launch {
//        while (isActive) {
//            val message = messageOutbox.receive()
//            envelopeOutbox.send(message.toEnvelope())
//        }
//    }

//    private val listeners: Map<NameToken, DeviceListener> = hub.devices.mapValues { (deviceNameToken, device) ->
//        object : DeviceListener {
//            override fun propertyChanged(propertyName: String, value: MetaItem?) {
//                if (value == null) return
//                scope.launch {
//                    val change = PropertyChangedMessage(
//                        sourceDevice = deviceNameToken.toString(),
//                        key = propertyName,
//                        value = value
//                    )
//                    messageOutbox.send(change)
//                }
//            }
//        }.also {
//            device.registerListener(it)
//        }
//    }

    public suspend fun respondMessage(message: DeviceMessage): DeviceMessage = try {
        val targetName = message.targetDevice?.toName() ?: Name.EMPTY
        val device = hub.getOrNull(targetName) ?: error("The device with name $targetName not found in $hub")
        DeviceController.respondMessage(device, targetName.toString(), message)
    } catch (ex: Exception) {
        DeviceMessage.error(ex, sourceDevice = hub.deviceName, targetDevice = message.sourceDevice)
    }
//
//    override suspend fun respond(request: Envelope): Envelope = try {
//        val targetName = request.meta[DeviceMessage.TARGET_KEY].string?.toName() ?: Name.EMPTY
//        val device = hub[targetName] ?: error("The device with name $targetName not found in $hub")
//        if (request.data == null) {
//            DeviceController.respondMessage(device, targetName.toString(), DeviceMessage.fromMeta(request.meta))
//                .toEnvelope()
//        } else {
//            DeviceController.respond(device, targetName.toString(), request)
//        }
//    } catch (ex: Exception) {
//        DeviceMessage.error(ex, sourceDevice = null).toEnvelope()
//    }
//
//    override fun consume(message: Envelope) {
//        // Fire the respond procedure and forget about the result
//        scope.launch {
//            respond(message)
//        }
//    }
}