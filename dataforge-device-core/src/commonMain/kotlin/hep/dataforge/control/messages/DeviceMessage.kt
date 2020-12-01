package hep.dataforge.control.messages

import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import hep.dataforge.names.Name
import hep.dataforge.names.asName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
public sealed class DeviceMessage{
    public abstract val sourceName: String?
    public abstract val targetName: String?
    public abstract val comment: String?


    public companion object {
        public val SOURCE_KEY: Name = DeviceMessage::sourceName.name.asName()
        public val TARGET_KEY: Name = DeviceMessage::targetName.name.asName()
        public val MESSAGE_ACTION_KEY: Name = DeviceMessage::action.name.asName()
        public val MESSAGE_KEY_KEY: Name = DeviceMessage::key.name.asName()
        public val MESSAGE_VALUE_KEY: Name = DeviceMessage::value.name.asName()

        public const val OK_STATUS: String = "OK"
        public const val FAIL_STATUS: String = "FAIL"
        public const val PROPERTY_CHANGED_ACTION: String = "event.propertyChanged"

        public fun error(
            cause: Throwable,
        ): DeviceErrorMessage = DeviceErrorMessage(
            errorMessage = cause.message,
            errorType = cause::class.simpleName,
            errorStackTrace = cause.stackTraceToString()
        )

        public fun fromMeta(meta: Meta): DeviceMessage = Json.decodeFromJsonElement(meta.toJson())
    }
}

@Serializable
@SerialName("property.changed")
public data class PropertyChangedMessage(
    public val key: String,
    public val value: MetaItem<*>?,
    override val sourceName: String? = null,
    override val targetName: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

@Serializable
@SerialName("property.set")
public data class PropertySetMessage(
    public val key: String,
    public val value: MetaItem<*>,
    override val sourceName: String? = null,
    override val targetName: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

@Serializable
@SerialName("property.read")
public data class PropertyReadMessage(
    public val key: String,
    override val sourceName: String? = null,
    override val targetName: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

@Serializable
@SerialName("action.execute")
public data class ActionExecuteMessage(
    public val action: String,
    public val argument: MetaItem<*>?,
    override val sourceName: String? = null,
    override val targetName: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

@Serializable
@SerialName("action.result")
public data class ActionResultMessage(
    public val action: String,
    public val result: MetaItem<*>?,
    override val sourceName: String? = null,
    override val targetName: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

@Serializable
@SerialName("error")
public data class DeviceErrorMessage(
    public val errorMessage: String?,
    public val errorType: String? = null,
    public val errorStackTrace: String? = null,
    override val sourceName: String? = null,
    override val targetName: String? = null,
    override val comment: String? = null,
) : DeviceMessage()


public fun DeviceMessage.toMeta(): JsonMeta = Json.encodeToJsonElement(this).toMetaItem().node!!

public fun DeviceMessage.toEnvelope(): SimpleEnvelope = SimpleEnvelope(toMeta(), null)
