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
import space.kscience.dataforge.names.asName

@Serializable
public sealed interface Message {
    public val sourceDevice: Name?
    public val targetDevice: Name?
    public val time: Instant

    /**
     * Update the source device name for composition. If the original name is null, the resulting name is also null.
     */
    public fun changeSource(block: (Name) -> Name): Message
}

@Serializable
public sealed class DeviceMessage: Message {
    public abstract val comment: String?

    /**
     * Update the source device name for composition. If the original name is null, the resulting name is also null.
     */
    public abstract override fun changeSource(block: (Name) -> Name): DeviceMessage

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
 * Message with serialized device failure
 */
@Serializable
public data class DeviceFailureMessage(
    val failure: SerializableDeviceFailure,
    override val sourceDevice: Name?,
    override val targetDevice: Name? = null,
    override val time: Instant = Clock.System.now()
) : Message {
    override fun changeSource(block: (Name) -> Name): DeviceFailureMessage = copy(sourceDevice = sourceDevice?.let(block))
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
    override val targetDevice: Name?,
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
    val failure: SerializableDeviceFailure? = null,
    public val requestId: String,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
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
    val contentId: String,
    val contentMeta: Meta,
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
    val state: LifecycleState,
    override val sourceDevice: Name = Name.EMPTY,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * Device log message: Messages from specific devices about their internal state
 * and operations. Always have a source device and tied to a specific device context.
 */
@Serializable
@SerialName("device.log")
public data class DeviceLogMessage(
    val message: String,
    val data: Meta? = null,
    override val sourceDevice: Name,
    override val targetDevice: Name? = null,
    override val comment: String? = null,
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : DeviceMessage() {
    override fun changeSource(block: (Name) -> Name): DeviceMessage = copy(sourceDevice = block(sourceDevice))
}

/**
 * System log message: Infrastructure events and system notifications
 * that aren't tied to a specific device. Used for resource management
 * and system state reporting.
 */
@Serializable
@SerialName("system.log")
public data class SystemLogMessage(
    val message: String,
    val component: String,
    override val sourceDevice: Name = "system".asName(),
    override val targetDevice: Name? = null,
    val details: Map<String, String> = emptyMap(),
    @EncodeDefault override val time: Instant = Clock.System.now(),
) : Message {
    override fun changeSource(block: (Name) -> Name): Message = copy(sourceDevice = block(sourceDevice))
}

/**
 * Transaction messages for coordinating transactional operations
 */
@Serializable
public sealed interface TransactionMessage : Message {
    public val transactionId: String

    /**
     * Message about transaction starting
     */
    @Serializable
    @SerialName("transaction.started")
    public data class TransactionStartedMessage(
        override val transactionId: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : TransactionMessage {
        override fun changeSource(block: (Name) -> Name): TransactionMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about transaction commit
     */
    @Serializable
    @SerialName("transaction.committed")
    public data class TransactionCommittedMessage(
        override val transactionId: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : TransactionMessage {
        override fun changeSource(block: (Name) -> Name): TransactionMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about transaction rollback
     */
    @Serializable
    @SerialName("transaction.rolled_back")
    public data class TransactionRolledBackMessage(
        override val transactionId: String,
        val reason: String?,
        val errorType: String?,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : TransactionMessage {
        override fun changeSource(block: (Name) -> Name): TransactionMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about creating a savepoint in a transaction
     */
    @Serializable
    @SerialName("transaction.savepoint")
    public data class TransactionSavepointMessage(
        override val transactionId: String,
        val savepointName: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now()
    ) : TransactionMessage {
        override fun changeSource(block: (Name) -> Name): TransactionMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }
}

/**
 * Device state messages for device lifecycle events
 */
@Serializable
public sealed interface DeviceStateMessage : Message {
    public val deviceName: String

    /**
     * Message about device addition
     */
    @Serializable
    public data class DeviceStateAddedMessage(
        override val deviceName: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : DeviceStateMessage {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about device startup
     */
    @Serializable
    public data class DeviceStateStartedMessage(
        override val deviceName: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : DeviceStateMessage {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about device stopping
     */
    @Serializable
    public data class DeviceStateStoppedMessage(
        override val deviceName: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : DeviceStateMessage {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about device removal
     */
    @Serializable
    public data class DeviceStateRemovedMessage(
        override val deviceName: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : DeviceStateMessage {
        override fun changeSource(block: (Name) -> Name): Message = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about device failure
     */
    @Serializable
    public data class DeviceStateFailedMessage(
        override val deviceName: String,
        val failure: SerializableDeviceFailure,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : DeviceStateMessage {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }

    /**
     * Message about device detachment
     */
    @Serializable
    public data class DeviceStateDetachedMessage(
        override val deviceName: String,
        override val sourceDevice: Name? = null,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : DeviceStateMessage {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage
                = copy(sourceDevice = sourceDevice?.let(block))
    }
}

/**
 * Interface for metrics
 */
@Serializable
public sealed interface MetricMessage : Message {
    public val metricName: String
    public val tags: Map<String, String>

    /**
     * Metric value message
     */
    @Serializable
    public data class MetricValueMessage(
        override val metricName: String,
        val value: Double,
        override val sourceDevice: Name,
        override val tags: Map<String, String> = emptyMap(),
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : MetricMessage {
        override fun changeSource(block: (Name) -> Name): MetricMessage
                = copy(sourceDevice = block(sourceDevice))
    }

    /**
     * Metric counter message
     */
    @Serializable
    public data class MetricCounterMessage(
        override val metricName: String,
        val increment: Double = 1.0,
        override val tags: Map<String, String> = emptyMap(),
        override val sourceDevice: Name,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : MetricMessage {
        override fun changeSource(block: (Name) -> Name): MetricMessage
                = copy(sourceDevice = block(sourceDevice))
    }

    /**
     * Operation duration metric message
     */
    @Serializable
    public data class MetricDurationMessage(
        override val metricName: String,
        val durationMs: Long,
        override val tags: Map<String, String> = emptyMap(),
        override val sourceDevice: Name,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : MetricMessage {
        override fun changeSource(block: (Name) -> Name): Message = copy(sourceDevice = block(sourceDevice))
    }

    /**
     * Metric distribution message
     */
    @Serializable
    public data class MetricDistributionMessage(
        override val metricName: String,
        val value: Double,
        override val tags: Map<String, String> = emptyMap(),
        override val sourceDevice: Name,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : MetricMessage {
        override fun changeSource(block: (Name) -> Name): MetricMessage
                = copy(sourceDevice = block(sourceDevice))
    }

    /**
     * Metric gauge message
     */
    @Serializable
    public data class MetricGaugeMessage(
        override val metricName: String,
        val value: Double,
        override val tags: Map<String, String> = emptyMap(),
        override val sourceDevice: Name,
        override val targetDevice: Name? = null,
        override val time: Instant = Clock.System.now()
    ) : MetricMessage {
        override fun changeSource(block: (Name) -> Name): MetricMessage
                = copy(sourceDevice = block(sourceDevice))
    }

}


public fun DeviceMessage.toMeta(): Meta = Json.encodeToJsonElement(this).toMeta()

public fun DeviceMessage.toEnvelope(): Envelope = Envelope(toMeta(), null)
