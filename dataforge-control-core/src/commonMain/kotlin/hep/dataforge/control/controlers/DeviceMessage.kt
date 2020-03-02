package hep.dataforge.control.controlers

import hep.dataforge.meta.Meta
import hep.dataforge.meta.scheme.*

class PropertyValue : Scheme() {
    var name by string { error("Name property not defined") }
    var value by item()

    companion object : SchemeSpec<PropertyValue>(::PropertyValue)
}

open class DeviceMessage : Scheme() {
    var id by item()
    var source by string()//TODO consider replacing by item
    var target by string()
    var action by string()
    var status by string()

    companion object : SchemeSpec<DeviceMessage>(::DeviceMessage) {
        const val MESSAGE_ACTION_KEY = "action"
        const val MESSAGE_PROPERTY_NAME_KEY = "propertyName"
        const val MESSAGE_VALUE_KEY = "value"
        const val RESPONSE_OK_STATUS = "response.OK"
        const val EVENT_STATUS = "event.propertyChange"

        fun ok(request: DeviceMessage? = null, block: DeviceMessage.() -> Unit): Meta {
            return DeviceMessage {
                id = request?.id
                status = RESPONSE_OK_STATUS
            }.apply(block).toMeta()
        }
    }
}

class DevicePropertyMessage : DeviceMessage() {
    //TODO add multiple properties in the same message
    var property by spec(PropertyValue)

    fun property(builder: PropertyValue.() -> Unit) {
        this.property = PropertyValue.invoke(builder)
    }

    companion object : SchemeSpec<DevicePropertyMessage>(::DevicePropertyMessage) {
        fun ok(request: DeviceMessage? = null, block: DevicePropertyMessage.() -> Unit): Meta {
            return DevicePropertyMessage {
                id = request?.id
                property {
                    name
                }
                status = RESPONSE_OK_STATUS
            }.apply(block).toMeta()
        }
    }
}