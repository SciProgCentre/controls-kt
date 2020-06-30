package hep.dataforge.control.controllers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.controllers.DeviceMessage.Companion.PROPERTY_CHANGED_ACTION
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.wrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Binary

/**
 * A consumer of envelopes
 */
interface Consumer {
    fun consume(message: Envelope): Unit
}

class MessageController(
    val device: Device,
    val deviceTarget: String,
    val scope: CoroutineScope = device.scope
) : Consumer, Responder, DeviceListener {

    init {
        device.registerListener(this, this)
    }

    private val outputChannel = Channel<Envelope>(Channel.CONFLATED)

    suspend fun respondMessage(
        request: DeviceMessage
    ): DeviceMessage = if (request.target != null && request.target != deviceTarget) {
        DeviceMessage.fail {
            comment = "Wrong target name $deviceTarget expected but ${request.target} found"
        }
    } else try {
        when (val action = request.action ?: error("Action is not defined in message")) {
            Device.GET_PROPERTY_ACTION -> {
                val property = request.property ?: error("Payload is not defined or not a property")
                val propertyName: String = property.name
                val result = device.getProperty(propertyName)

                DeviceMessage.ok {
                    this.source = deviceTarget
                    this.target = request.source
                    property {
                        name = propertyName
                        value = result
                    }
                }
            }
            Device.SET_PROPERTY_ACTION -> {
                val property = request.property ?: error("Payload is not defined or not a property")
                val propertyName: String = property.name
                val propertyValue = property.value
                if (propertyValue == null) {
                    device.invalidateProperty(propertyName)
                } else {
                    device.setProperty(propertyName, propertyValue)
                }
                DeviceMessage.ok {
                    this.source = deviceTarget
                    this.target = request.source
                    property {
                        name = propertyName
                    }
                }
            }
            else -> {
                val value = request.value
                val result = device.call(action, value)
                DeviceMessage.ok {
                    this.source = deviceTarget
                    this.action = action
                    this.value = result
                }
            }
        }
    } catch (ex: Exception) {
        DeviceMessage.fail {
            comment = ex.message
        }
    }

    override fun consume(message: Envelope) {
        // Fire the respond procedure and forget about the result
        scope.launch {
            respond(message)
        }
    }

    override suspend fun respond(request: Envelope): Envelope {
        val requestMessage = DeviceMessage.wrap(request.meta)
        val responseMessage = respondMessage(requestMessage)
        return SimpleEnvelope(responseMessage.toMeta(), Binary.EMPTY)
    }

    override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        if (value == null) return
        scope.launch {
            val change = DeviceMessage.ok {
                this.source = deviceTarget
                action = PROPERTY_CHANGED_ACTION
                property {
                    name = propertyName
                    this.value = value
                }
            }
            val envelope = SimpleEnvelope(change.toMeta(), Binary.EMPTY)

            outputChannel.send(envelope)
        }
    }

    fun output() = outputChannel.consumeAsFlow()


    companion object {
    }
}