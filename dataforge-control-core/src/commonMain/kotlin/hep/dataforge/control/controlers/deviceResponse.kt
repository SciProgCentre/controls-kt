package hep.dataforge.control.controlers

import hep.dataforge.control.api.Device
import hep.dataforge.io.Envelope
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.Meta
import hep.dataforge.meta.set
import kotlinx.io.EmptyBinary

suspend fun Device.respond(target: String, request: Envelope, action: String): Envelope {
    val metaResult = when (action) {
        Device.GET_PROPERTY_ACTION -> {
            val message = DevicePropertyMessage.wrap(request.meta)
            val property = message.property ?: error("Property item not defined")
            val propertyName: String = property.name
            val result = getProperty(propertyName)

            DevicePropertyMessage.ok {
                this.source = target
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
                invalidateProperty(propertyName)
            } else {
                setProperty(propertyName, propertyValue)
            }
            DevicePropertyMessage.ok {
                this.source = target
                this.target = message.source
                property {
                    name = propertyName
                }
            }
        }
        else -> {
            val data: Meta? = request.meta[DeviceMessage.MESSAGE_VALUE_KEY].node
            val result = action(action, data)
            DeviceMessage.ok {
                this.source = target
                config[DeviceMessage.MESSAGE_ACTION_KEY] = action
                config[DeviceMessage.MESSAGE_VALUE_KEY] = result
            }
        }
    }
    return SimpleEnvelope(metaResult, EmptyBinary)
}

suspend fun Device.respond(target: String, request: Envelope): Envelope {
    val action: String = request.meta[DeviceMessage.MESSAGE_ACTION_KEY].string ?: error("Action not defined")
    return respond(target, request, action)
}