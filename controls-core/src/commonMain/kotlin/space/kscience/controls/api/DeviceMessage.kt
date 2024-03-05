@file:OptIn(ExperimentalSerializationApi::class)

package space.kscience.controls.api

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.toJson
import space.kscience.dataforge.meta.toMeta
import space.kscience.dataforge.names.Name

@Serializable
public sealed class DeviceMessage {
    public abstract val sourceDevice: Name?
    public abstract val targetDevice: Name?
    public abstract val comment: String?
    public abstract val time: Instant

    /**
     * Update the source device name for composition. If the original name is null, the resulting name is also null.
     */
    public abstract fun changeSource(block: (Name) -> Name): DeviceMessage

    public companion object {
        public fun error(
            cause: Throwable,
            sourceDevice: Name,
            targetDevice: Name? = null,
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
 *
 */
@Serializable
@SerialName("property.changed")
public data class PropertyChangedMessage(
    public val property: String,
    public val value: Meta,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * A command to set or invalidate property. [targetDevice] is mandatory.
 */
@Serializable
@SerialName("property.set")
public data class PropertySetMessage(
    public val property: String,
    public val value: Meta,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * A command to request property value asynchronously. [targetDevice] is mandatory.
 * The property value should be returned asynchronously via [PropertyChangedMessage].
 */
@Serializable
@SerialName("property.get")
public data class PropertyGetMessage(
    public val property: String,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * Request device description. The result is returned in form of [DescriptionMessage]
 */
@Serializable
@SerialName("description.get")
public data class GetDescriptionMessage(
    override val sourceDevice: Name? = null,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * The full device description message
 */
@Serializable
@SerialName("description")
public data class DescriptionMessage(
    val description: Meta,
    val properties: Collection<PropertyDescriptor>,
    val actions: Collection<ActionDescriptor>,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * A request to execute an action. [targetDevice] is mandatory
 *
 * @param requestId action request id that should be returned in a response
 */
@Serializable
@SerialName("action.execute")
public data class ActionExecuteMessage(
    public val action: String,
    public val argument: Meta?,
    public val requestId: String,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * Asynchronous action result. [sourceDevice] is mandatory
 *
 * @param requestId request id passed in the request
 */
@Serializable
@SerialName("action.result")
public data class ActionResultMessage(
    public val action: String,
    public val result: Meta?,
    public val requestId: String,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * Notifies listeners that a new binary with given [binaryID] is available. The binary itself could not be provided via [DeviceMessage] API.
 */
@Serializable
@SerialName("binary.notification")
public data class BinaryNotificationMessage(
    val binaryID: String,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * The message states that the message is received, but no meaningful response is produced.
 * This message could be used for a heartbeat.
 */
@Serializable
@SerialName("empty")
public data class EmptyDeviceMessage(
    override val sourceDevice: Name? = null,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * Information log message
 */
@Serializable
@SerialName("log")
public data class DeviceLogMessage(
    val message: String,
    val data: Meta? = null,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * The evaluation of the message produced a service error
 */
@Serializable
@SerialName("error")
public data class DeviceErrorMessage(
    public val errorMessage: String?,
    public val errorType: String? = null,
    public val errorStackTrace: String? = null,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * Device [Device.lifecycleState] is changed
 */
@Serializable
@SerialName("lifecycle")
public data class DeviceLifeCycleMessage(
    val state: DeviceLifecycleState,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}


public fun DeviceMessage.toMeta(): Meta = Json.encodeToJsonElement(this).toMeta()

public fun DeviceMessage.toEnvelope(): Envelope = Envelope(toMeta(), null)
