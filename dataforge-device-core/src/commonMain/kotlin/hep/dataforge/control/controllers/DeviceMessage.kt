package hep.dataforge.control.controllers

import hep.dataforge.control.controllers.DeviceMessage.Companion.PAYLOAD_VALUE_KEY
import hep.dataforge.meta.*
import hep.dataforge.names.asName


class DeviceMessage : Scheme() {
    var id by string { error("The message id must not be empty") }
    var parent by string()
    var origin by string()
    var target by string()
    var action by string(default = MessageController.GET_PROPERTY_ACTION, key = MESSAGE_ACTION_KEY)
    var comment by string()
    var status by string(RESPONSE_OK_STATUS)
    var payload: List<MessagePayload>
        get() = config.getIndexed(MESSAGE_PAYLOAD_KEY).values.map { MessagePayload.wrap(it.node!!) }
        set(value) {
            config[MESSAGE_PAYLOAD_KEY] = value.map { it.config }
        }

    /**
     * Append a payload to this message according to the given scheme
     */
    fun <T : Configurable> append(spec: Specification<T>, block: T.() -> Unit): T =
        spec.invoke(block).also { config.append(MESSAGE_PAYLOAD_KEY, it) }

    companion object : SchemeSpec<DeviceMessage>(::DeviceMessage) {
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
            parent = request?.id
        }.apply(block)

        inline fun fail(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {}
        ): DeviceMessage = DeviceMessage {
            parent = request?.id
            status = RESPONSE_FAIL_STATUS
        }.apply(block)
    }
}

class MessagePayload : Scheme() {
    var name by string { error("Property name could not be empty") }
    var value by item(key = PAYLOAD_VALUE_KEY)

    companion object : SchemeSpec<MessagePayload>(::MessagePayload)
}

@DFBuilder
fun DeviceMessage.property(block: MessagePayload.() -> Unit): MessagePayload = append(MessagePayload, block)
