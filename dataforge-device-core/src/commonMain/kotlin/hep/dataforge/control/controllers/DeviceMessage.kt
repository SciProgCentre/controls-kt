package hep.dataforge.control.controllers

import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import hep.dataforge.names.Name
import hep.dataforge.names.asName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public class DeviceMessage : Scheme() {
    public var action: String by string { error("Action not defined") }
    public var status: String by string(default = RESPONSE_OK_STATUS)
    public var sourceName: String? by string()
    public var targetName: String? by string()
    public var comment: String? by string()
    public var key: String? by string()
    public var value: MetaItem<*>? by item()

    public companion object : SchemeSpec<DeviceMessage>(::DeviceMessage), KSerializer<DeviceMessage> {
        public val SOURCE_KEY: Name = DeviceMessage::sourceName.name.asName()
        public val TARGET_KEY: Name = DeviceMessage::targetName.name.asName()
        public val MESSAGE_ACTION_KEY: Name = DeviceMessage::action.name.asName()
        public val MESSAGE_KEY_KEY: Name = DeviceMessage::key.name.asName()
        public val MESSAGE_VALUE_KEY: Name = DeviceMessage::value.name.asName()

        public const val RESPONSE_OK_STATUS: String = "response.OK"
        public const val RESPONSE_FAIL_STATUS: String = "response.FAIL"
        public const val PROPERTY_CHANGED_ACTION: String = "event.propertyChange"

        public inline fun ok(
            request: DeviceMessage? = null,
            block: DeviceMessage.() -> Unit = {},
        ): DeviceMessage = DeviceMessage {
            targetName = request?.sourceName
        }.apply(block)

        public inline fun fail(
            request: DeviceMessage? = null,
            cause: Throwable? = null,
            block: DeviceMessage.() -> Unit = {},
        ): DeviceMessage = DeviceMessage {
            targetName = request?.sourceName
            status = RESPONSE_FAIL_STATUS
            if (cause != null) {
                configure {
                    set("error.type", cause::class.simpleName)
                    set("error.message", cause.message)
                    //set("error.trace", ex.stackTraceToString())
                }
                comment = cause.message
            }
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

public fun DeviceMessage.wrap(): SimpleEnvelope = SimpleEnvelope(this.config, null)
