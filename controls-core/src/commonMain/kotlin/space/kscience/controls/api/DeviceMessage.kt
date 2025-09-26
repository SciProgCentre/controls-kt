@file:OptIn(ExperimentalSerializationApi::class)

package space.kscience.controls.api

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
import kotlin.time.Instant

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
            time: Instant,
            cause: Throwable,
            sourceDevice: Name,
            targetDevice: Name? = null,
        ): DeviceErrorMessage = DeviceErrorMessage(
            time = time,
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
    override val time: Instant,
    public val property: String,
    public val value: Meta,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * A command to set or invalidate property. [targetDevice] is mandatory.
 */
@Serializable
@SerialName("property.set")
public data class PropertySetMessage(
    override val time: Instant,
    public val property: String,
    public val value: Meta,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name?,
    override val comment: String? = null,
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
    override val time: Instant,
    public val property: String,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * Request device description. The result is returned in form of [DescriptionMessage]
 */
@Serializable
@SerialName("description.get")
public data class GetDescriptionMessage(
    override val time: Instant,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * The full device description message
 */
@Serializable
@SerialName("description")
public data class DescriptionMessage(
    override val time: Instant,
    val description: Meta,
    val properties: Collection<PropertyDescriptor>,
    val actions: Collection<ActionDescriptor>,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
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
    override val time: Instant,
    public val action: String,
    public val argument: Meta?,
    public val requestId: String,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name,
    override val comment: String? = null,
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
    override val time: Instant,
    public val action: String,
    public val result: Meta?,
    public val requestId: String,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * Notifies listeners that a new binary with given [contentId] and [contentMeta] is available.
 *
 * [contentMeta] includes public information that could be shared with loop subscribers. It should not contain sensitive data.
 *
 * The binary itself could not be provided via [DeviceMessage] API.
 * [space.kscience.controls.peer.PeerConnection] must be used instead
 */
@Serializable
@SerialName("binary.notification")
public data class BinaryNotificationMessage(
    override val time: Instant,
    val contentId: String,
    val contentMeta: Meta,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
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
    override val time: Instant,
    override val sourceDevice: Name? = null,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = sourceDevice?.let(block))
}

/**
 * Information log message
 */
@Serializable
@SerialName("log")
public data class DeviceLogMessage(
    override val time: Instant,
    val message: String,
    val data: Meta? = null,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * The evaluation of the message produced a service error
 */
@Serializable
@SerialName("error")
public data class DeviceErrorMessage(
    override val time: Instant,
    public val errorMessage: String?,
    public val errorType: String? = null,
    public val errorStackTrace: String? = null,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * Device [Device.lifecycleState] is changed
 */
@Serializable
@SerialName("lifecycle")
public data class DeviceLifeCycleMessage(
    override val time: Instant,
    val state: LifecycleState,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}


public fun DeviceMessage.toMeta(): Meta = Json.encodeToJsonElement(this).toMeta()

public fun DeviceMessage.toEnvelope(): Envelope = Envelope(toMeta(), null)
