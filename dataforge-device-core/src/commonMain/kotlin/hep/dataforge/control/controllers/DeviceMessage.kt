package hep.dataforge.control.controllers

import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import hep.dataforge.names.Name
import hep.dataforge.names.asName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
public data class DeviceMessage(
    public val action: String,
    public val status: String = OK_STATUS,
    public val sourceName: String? = null,
    public val targetName: String? = null,
    public val comment: String? = null,
    public val key: String? = null,
    public val value: MetaItem<*>? = null,
) {
    public companion object {
        public val SOURCE_KEY: Name = DeviceMessage::sourceName.name.asName()
        public val TARGET_KEY: Name = DeviceMessage::targetName.name.asName()
        public val MESSAGE_ACTION_KEY: Name = DeviceMessage::action.name.asName()
        public val MESSAGE_KEY_KEY: Name = DeviceMessage::key.name.asName()
        public val MESSAGE_VALUE_KEY: Name = DeviceMessage::value.name.asName()

        public const val OK_STATUS: String = "OK"
        public const val FAIL_STATUS: String = "FAIL"
        public const val PROPERTY_CHANGED_ACTION: String = "event.propertyChanged"

        private fun Throwable.toMeta(): Meta = Meta {
            "type" put this::class.simpleName
            "message" put message
            "trace" put stackTraceToString()
        }

        public fun fail(
            cause: Throwable,
            action: String = "undefined",
        ): DeviceMessage = DeviceMessage(
            action = action,
            status = FAIL_STATUS,
            value = cause.toMeta().asMetaItem()
        )

        public fun fromMeta(meta: Meta): DeviceMessage = Json.decodeFromJsonElement(meta.toJson())
    }
}


public fun DeviceMessage.ok(): DeviceMessage =
    copy(status = DeviceMessage.OK_STATUS)

public fun DeviceMessage.respondsTo(request: DeviceMessage): DeviceMessage =
    copy(sourceName = request.targetName, targetName = request.sourceName)

public fun DeviceMessage.toMeta(): JsonMeta = Json.encodeToJsonElement(this).toMetaItem().node!!

public fun DeviceMessage.toEnvelope(): SimpleEnvelope = SimpleEnvelope(toMeta(), null)
