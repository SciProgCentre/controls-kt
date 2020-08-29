package hep.dataforge.control.controllers

import hep.dataforge.control.controllers.DeviceController.Companion.GET_PROPERTY_ACTION
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import hep.dataforge.names.asName
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor

class DeviceMessage : Scheme() {
    var source by string(key = SOURCE_KEY)
    var target by string(key = TARGET_KEY)
    var type by string(default = GET_PROPERTY_ACTION, key = MESSAGE_TYPE_KEY)
    var comment by string()
    var status by string(RESPONSE_OK_STATUS)
    var data: List<MessageData>
        get() = config.getIndexed(MESSAGE_DATA_KEY).values.map { MessageData.wrap(it.node!!) }
        set(value) {
            config[MESSAGE_DATA_KEY] = value.map { it.config }
        }

    /**
     * Append a payload to this message according to the given scheme
     */
    fun <T : Configurable> append(spec: Specification<T>, block: T.() -> Unit): T =
        spec.invoke(block).also { config.append(MESSAGE_DATA_KEY, it) }

    companion object : SchemeSpec<DeviceMessage>(::DeviceMessage), KSerializer<DeviceMessage> {
        val SOURCE_KEY = "source".asName()
        val TARGET_KEY = "target".asName()
        val MESSAGE_TYPE_KEY = "type".asName()
        val MESSAGE_DATA_KEY = "data".asName()

        const val RESPONSE_OK_STATUS = "response.OK"
        const val RESPONSE_FAIL_STATUS = "response.FAIL"
        const val PROPERTY_CHANGED_ACTION = "event.propertyChange"

        inline fun ok(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {}
        ): DeviceMessage = DeviceMessage {
            target = request?.source
        }.apply(block)

        inline fun fail(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {}
        ): DeviceMessage = DeviceMessage {
            target = request?.source
            status = RESPONSE_FAIL_STATUS
        }.apply(block)

        override val descriptor: SerialDescriptor = MetaSerializer.descriptor

        override fun deserialize(decoder: Decoder): DeviceMessage {
            val meta = MetaSerializer.deserialize(decoder)
            return wrap(meta)
        }

        override fun serialize(encoder: Encoder, value: DeviceMessage) {
            MetaSerializer.serialize(encoder, value.toMeta())
        }
    }
}

class MessageData : Scheme() {
    var name by string { error("Property name could not be empty") }
    var value by item(key = DATA_VALUE_KEY)

    companion object : SchemeSpec<MessageData>(::MessageData) {
        val DATA_VALUE_KEY = "value".asName()
    }
}

@DFBuilder
fun DeviceMessage.data(block: MessageData.() -> Unit): MessageData = append(MessageData, block)

fun DeviceMessage.wrap() = SimpleEnvelope(this.config, null)
