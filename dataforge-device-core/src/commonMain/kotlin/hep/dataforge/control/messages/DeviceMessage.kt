package hep.dataforge.control.messages

import hep.dataforge.io.SimpleEnvelope
import hep.dataforge.meta.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
public sealed class DeviceMessage {
    public abstract val sourceDevice: String?
    public abstract val targetDevice: String?
    public abstract val comment: String?


    public companion object {
        public fun error(
            cause: Throwable,
            sourceDevice: String,
            targetDevice: String? = null,
        ): DeviceErrorMessage = DeviceErrorMessage(
            errorMessage = cause.message,
            errorType = cause::class.simpleName,
            errorStackTrace = cause.stackTraceToString(),
            sourceDevice = sourceDevice,
            targetDevice = targetDevice
        )

        public fun fromMeta(meta: Meta): DeviceMessage = Json.decodeFromJsonElement(meta.toJson())
    }
}

/**
 * Notify that property is changed. [sourceDevice] is mandatory.
 * [property] corresponds to property name.
 * [value] could be null if the property is invalidated.
 *
 */
@Serializable
@SerialName("property.changed")
public data class PropertyChangedMessage(
    public val property: String,
    public val value: MetaItem<*>?,
    override val sourceDevice: String,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * A command to set or invalidate property. [targetDevice] is mandatory.
 */
@Serializable
@SerialName("property.set")
public data class PropertySetMessage(
    public val property: String,
    public val value: MetaItem<*>?,
    override val sourceDevice: String? = null,
    override val targetDevice: String,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * A command to request property value asynchronously. [targetDevice] is mandatory.
 * The property value should be returned asynchronously via [PropertyChangedMessage].
 */
@Serializable
@SerialName("property.get")
public data class PropertyGetMessage(
    public val property: String,
    override val sourceDevice: String? = null,
    override val targetDevice: String,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * Request device description. The result is returned in form of [DescriptionMessage]
 */
@Serializable
@SerialName("description.get")
public data class GetDescriptionMessage(
    override val sourceDevice: String? = null,
    override val targetDevice: String,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * The full device description message
 */
@Serializable
@SerialName("description")
public data class DescriptionMessage(
    val description: Meta,
    override val sourceDevice: String,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * A request to execute an action. [targetDevice] is mandatory
 */
@Serializable
@SerialName("action.execute")
public data class ActionExecuteMessage(
    public val action: String,
    public val argument: MetaItem<*>?,
    override val sourceDevice: String? = null,
    override val targetDevice: String,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * Asynchronous action result. [sourceDevice] is mandatory
 */
@Serializable
@SerialName("action.result")
public data class ActionResultMessage(
    public val action: String,
    public val result: MetaItem<*>?,
    override val sourceDevice: String,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * Notifies listeners that a new binary with given [binaryID] is available. The binary itself could not be provided via [DeviceMessage] API.
 */
@Serializable
@SerialName("binary.notification")
public data class BinaryNotificationMessage(
    val binaryID: String,
    override val sourceDevice: String,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * The message states that the message is received, but no meaningful response is produced.
 * This message could be used for a heartbeat.
 */
@Serializable
@SerialName("empty")
public data class EmptyDeviceMessage(
    override val sourceDevice: String? = null,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * Information log message
 */
@Serializable
@SerialName("log")
public data class DeviceLogMessage(
    val message: String,
    val data: MetaItem<*>? = null,
    override val sourceDevice: String? = null,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()

/**
 * The evaluation of the message produced a service error
 */
@Serializable
@SerialName("error")
public data class DeviceErrorMessage(
    public val errorMessage: String?,
    public val errorType: String? = null,
    public val errorStackTrace: String? = null,
    override val sourceDevice: String,
    override val targetDevice: String? = null,
    override val comment: String? = null,
) : DeviceMessage()


public fun DeviceMessage.toMeta(): JsonMeta = Json.encodeToJsonElement(this).toMetaItem().node!!

public fun DeviceMessage.toEnvelope(): SimpleEnvelope = SimpleEnvelope(toMeta(), null)
