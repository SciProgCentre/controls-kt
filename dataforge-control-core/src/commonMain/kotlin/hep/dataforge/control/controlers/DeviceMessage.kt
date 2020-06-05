package hep.dataforge.control.controlers

import hep.dataforge.meta.*
import hep.dataforge.names.asName

open class DeviceMessage : Scheme() {
    var id by item()
    var source by string()//TODO consider replacing by item
    var target by string()
    var comment by string()
    var action by string(key = MESSAGE_ACTION_KEY)
    var status by string(RESPONSE_OK_STATUS)
    var value by item(key = MESSAGE_VALUE_KEY)

    companion object : SchemeSpec<DeviceMessage>(::DeviceMessage) {
        val MESSAGE_ACTION_KEY = "action".asName()
        val MESSAGE_VALUE_KEY = "value".asName()
        const val RESPONSE_OK_STATUS = "response.OK"
        const val RESPONSE_FAIL_STATUS = "response.FAIL"

        fun ok(request: DeviceMessage? = null, block: DeviceMessage.() -> Unit = {}): DeviceMessage {
            return DeviceMessage {
                id = request?.id
            }.apply(block)
        }

        fun fail(request: DeviceMessage? = null,block: DeviceMessage.() -> Unit = {}): DeviceMessage {
            return DeviceMessage {
                id = request?.id
                status = RESPONSE_FAIL_STATUS
            }.apply(block)
        }
    }
}

class DevicePropertyMessage : DeviceMessage() {
    //TODO add multiple properties in the same message
    var property by spec(PropertyValue)

    fun property(builder: PropertyValue.() -> Unit) {
        this.property = PropertyValue.invoke(builder)
    }

    class PropertyValue : Scheme() {
        var name by string { error("Property name not defined") }
        var value by item()

        companion object : SchemeSpec<PropertyValue>(::PropertyValue)
    }

    companion object : SchemeSpec<DevicePropertyMessage>(::DevicePropertyMessage) {
        const val PROPERTY_CHANGED_ACTION = "event.propertyChange"
        fun ok(request: DeviceMessage? = null, block: DevicePropertyMessage.() -> Unit = {}): DeviceMessage {
            return DevicePropertyMessage {
                id = request?.id
                property {
                    name
                }
            }.apply(block)
        }
    }
}