package hep.dataforge.control.controllers

import hep.dataforge.control.controllers.DeviceController.Companion.GET_PROPERTY_ACTION
import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import hep.dataforge.names.Name
import hep.dataforge.names.asName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public class DeviceMessage : Scheme() {
    public var source: String? by string(key = SOURCE_KEY)
    public var target: String? by string(key = TARGET_KEY)
    public var type: String by string(default = GET_PROPERTY_ACTION, key = MESSAGE_TYPE_KEY)
    public var comment: String? by string()
    public var status: String by string(RESPONSE_OK_STATUS)
    public var data: List<MessageData>
        get() = config.getIndexed(MESSAGE_DATA_KEY).values.map { MessageData.wrap(it.node!!) }
        set(value) {
            config[MESSAGE_DATA_KEY] = value.map { it.config }
        }

    /**
     * Append a payload to this message according to the given scheme
     */
    public fun <T : Configurable> append(spec: Specification<T>, block: T.() -> Unit): T =
        spec.invoke(block).also { config.append(MESSAGE_DATA_KEY, it) }

    public companion object : SchemeSpec<DeviceMessage>(::DeviceMessage), KSerializer<DeviceMessage> {
        public val SOURCE_KEY: Name = "source".asName()
        public val TARGET_KEY: Name = "target".asName()
        public val MESSAGE_TYPE_KEY: Name = "type".asName()
        public val MESSAGE_DATA_KEY: Name = "data".asName()

        public const val RESPONSE_OK_STATUS: String = "response.OK"
        public const val RESPONSE_FAIL_STATUS: String = "response.FAIL"
        public const val PROPERTY_CHANGED_ACTION: String = "event.propertyChange"

        public inline fun ok(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {}
        ): DeviceMessage = DeviceMessage {
            target = request?.source
        }.apply(block)

        public inline fun fail(
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

public class MessageData : Scheme() {
    public var name: String by string { error("Property name could not be empty") }
    public var value: MetaItem<*>? by item(key = DATA_VALUE_KEY)

    public companion object : SchemeSpec<MessageData>(::MessageData) {
        public val DATA_VALUE_KEY: Name = "value".asName()
    }
}

@DFBuilder
public fun DeviceMessage.data(block: MessageData.() -> Unit): MessageData = append(MessageData, block)

public fun DeviceMessage.wrap(): SimpleEnvelope = SimpleEnvelope(this.config, null)
