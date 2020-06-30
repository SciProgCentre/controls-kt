package hep.dataforge.control.controllers

import hep.dataforge.control.controlers.DeviceMessage.Companion.PAYLOAD_VALUE_KEY
import hep.dataforge.meta.*
import hep.dataforge.names.asName
import hep.dataforge.names.plus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class DeviceMessage : Scheme() {
    var id by item()
    var source by string()//TODO consider replacing by item
    var target by string()
    var comment by string()
    var action by string(key = MESSAGE_ACTION_KEY)
    var status by string(RESPONSE_OK_STATUS)
    var payload by config(key = MESSAGE_PAYLOAD_KEY)

    var value by item(key = (MESSAGE_PAYLOAD_KEY + PAYLOAD_VALUE_KEY))

    /**
     * Set a payload for this message according to the given scheme
     */
    inline fun <T : Scheme> payload(spec: Specification<T>, block: T.() -> Unit): T =
        (payload?.let { spec.wrap(it) } ?: spec.empty().also { payload = it.config }).apply(block)

    companion object : SchemeSpec<DeviceMessage>(::DeviceMessage){
        val MESSAGE_ACTION_KEY = "action".asName()
        val MESSAGE_PAYLOAD_KEY = "payload".asName()
        val PAYLOAD_VALUE_KEY = "value".asName()
        const val RESPONSE_OK_STATUS = "response.OK"
        const val RESPONSE_FAIL_STATUS = "response.FAIL"
        const val PROPERTY_CHANGED_ACTION = "event.propertyChange"

        inline fun ok(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {}
        ): DeviceMessage = DeviceMessage {
            id = request?.id
        }.apply(block)

        inline fun fail(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {}
        ): DeviceMessage = DeviceMessage {
            id = request?.id
            status = RESPONSE_FAIL_STATUS
        }.apply(block)
    }
}

class PropertyPayload : Scheme() {
    var name by string { error("Property name could not be empty") }
    var value by item(key = PAYLOAD_VALUE_KEY)

    companion object : SchemeSpec<PropertyPayload>(::PropertyPayload)
}

@DFBuilder
inline fun DeviceMessage.property(block: PropertyPayload.() -> Unit): PropertyPayload = payload(PropertyPayload, block)

var DeviceMessage.property: PropertyPayload?
    get() = payload?.let { PropertyPayload.wrap(it) }
    set(value) {
        payload = value?.config
    }

