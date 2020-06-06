package hep.dataforge.control.controlers

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.PropertyChangeListener
import hep.dataforge.control.controlers.DevicePropertyMessage.Companion.PROPERTY_CHANGED_ACTION
import hep.dataforge.io.Envelope
import hep.dataforge.io.Responder
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.get
import hep.dataforge.meta.string
import hep.dataforge.meta.wrap
import kotlinx.io.Binary

interface MessageConsumer {
    fun consume(message: Envelope): Unit
}

class MessageController(
    val device: Device,
    val deviceTarget: String
) : Responder, PropertyChangeListener {

    init {
        device.registerListener(this, this)
    }

    var messageListener: MessageConsumer? = null

    override suspend fun respond(request: Envelope): Envelope {
        val responseMessage: DeviceMessage = try {
            when (val action = request.meta[DeviceMessage.MESSAGE_ACTION_KEY].string ?: error("Action not defined")) {
                Device.GET_PROPERTY_ACTION -> {
                    val message = DevicePropertyMessage.wrap(request.meta)
                    val property = message.property ?: error("Property item not defined")
                    val propertyName: String = property.name
                    val result = device.getProperty(propertyName)

                    DevicePropertyMessage.ok {
                        this.source = deviceTarget
                        this.target = message.source
                        property {
                            name = propertyName
                            value = result
                        }
                    }
                }
                Device.SET_PROPERTY_ACTION -> {
                    val message = DevicePropertyMessage.wrap(request.meta)
                    val property = message.property ?: error("Property item not defined")
                    val propertyName: String = property.name
                    val propertyValue = property.value
                    if (propertyValue == null) {
                        device.invalidateProperty(propertyName)
                    } else {
                        device.setProperty(propertyName, propertyValue)
                    }
                    DevicePropertyMessage.ok {
                        this.source = deviceTarget
                        this.target = message.source
                        property {
                            name = propertyName
                        }
                    }
                }
                else -> {
                    val value = request.meta[DeviceMessage.MESSAGE_VALUE_KEY]
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

        return SimpleEnvelope(responseMessage.toMeta(), Binary.EMPTY)
    }

    override fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        if (value == null) return
        messageListener?.let { listener ->
            val change = DevicePropertyMessage.ok {
                this.source = deviceTarget
                action = PROPERTY_CHANGED_ACTION
                property {
                    name = propertyName
                    this.value = value
                }
            }
            val envelope = SimpleEnvelope(change.toMeta(), Binary.EMPTY)
            listener.consume(envelope)
        }
    }

    companion object {
    }
}