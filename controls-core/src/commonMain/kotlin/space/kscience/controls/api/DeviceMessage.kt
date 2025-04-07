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
public sealed class Message {
    public abstract val sourceDevice: Name?
    public abstract val targetDevice: Name?
    public abstract val time: Instant

    /**
     * Update the source device name for composition. If the original name is null, the resulting name is also null.
     */
    public abstract fun changeSource(block: (Name) -> Name): Message
}

@Serializable
public sealed class DeviceMessage: Message() {
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
) : Message() {
    override fun changeSource(block: (Name) -> Name): Message = copy(sourceDevice = block(sourceDevice))
}

/**
 * Transaction messages for coordinating transactional operations
 */
@Serializable
public sealed class TransactionMessage : Message() {
    public abstract val transactionId: String


    @Serializable
    @SerialName("transaction.started")
    public data class Started(
        override val transactionId: String,
        override val sourceDevice: Name = "transaction.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : TransactionMessage() {
        override fun changeSource(block: (Name) -> Name): TransactionMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("transaction.committed")
    public data class Committed(
        override val transactionId: String,
        override val sourceDevice: Name = "transaction.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : TransactionMessage() {
        override fun changeSource(block: (Name) -> Name): TransactionMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("transaction.rolled_back")
    public data class RolledBack(
        override val transactionId: String,
        val errorMessage: String? = null,
        val errorType: String? = null,
        override val sourceDevice: Name = "transaction.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : TransactionMessage() {
        override fun changeSource(block: (Name) -> Name): TransactionMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("transaction.savepoint")
    public data class Savepoint(
        override val transactionId: String,
        val savepointId: String,
        override val sourceDevice: Name = "transaction.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : TransactionMessage() {
        override fun changeSource(block: (Name) -> Name): TransactionMessage = copy(sourceDevice = block(sourceDevice))
    }
}

/**
 * Device state messages for device lifecycle events
 */
@Serializable
public sealed class DeviceStateMessage : Message() {
    public abstract val deviceName: String

    @Serializable
    @SerialName("device.state.added")
    public data class Added(
        override val deviceName: String,
        override val sourceDevice: Name = "device.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : DeviceStateMessage() {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("device.state.started")
    public data class Started(
        override val deviceName: String,
        override val sourceDevice: Name = "device.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : DeviceStateMessage() {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("device.state.stopped")
    public data class Stopped(
        override val deviceName: String,
        override val sourceDevice: Name = "device.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : DeviceStateMessage() {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("device.state.removed")
    public data class Removed(
        override val deviceName: String,
        override val sourceDevice: Name = "device.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : DeviceStateMessage() {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("device.state.failed")
    public data class Failed(
        override val deviceName: String,
        val errorMessage: String,
        val errorType: String? = null,
        override val sourceDevice: Name = "device.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : DeviceStateMessage() {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("device.state.detached")
    public data class Detached(
        override val deviceName: String,
        override val sourceDevice: Name = "device.manager".asName(),
        override val targetDevice: Name? = null,
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : DeviceStateMessage() {
        override fun changeSource(block: (Name) -> Name): DeviceStateMessage = copy(sourceDevice = block(sourceDevice))
    }
}

/**
 * Metric messages for monitoring and observability
 */
@Serializable
public sealed class MetricMessage : Message() {
    public abstract val name: String

    @Serializable
    @SerialName("metrics.value")
    public data class Value(
        override val name: String,
        val value: Double,
        override val sourceDevice: Name = "metrics".asName(),
        override val targetDevice: Name? = null,
        val tags: Map<String, String> = emptyMap(),
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : MetricMessage() {
        override fun changeSource(block: (Name) -> Name): MetricMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("metrics.counter")
    public data class Counter(
        override val name: String,
        val increment: Double = 1.0,
        override val sourceDevice: Name = "metrics".asName(),
        override val targetDevice: Name? = null,
        val tags: Map<String, String> = emptyMap(),
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : MetricMessage() {
        override fun changeSource(block: (Name) -> Name): MetricMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("metrics.duration")
    public data class Duration(
        override val name: String,
        val durationMs: Long,
        override val sourceDevice: Name = "metrics".asName(),
        override val targetDevice: Name? = null,
        val tags: Map<String, String> = emptyMap(),
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : MetricMessage() {
        override fun changeSource(block: (Name) -> Name): MetricMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("metrics.distribution")
    public data class Distribution(
        override val name: String,
        val value: Double,
        override val sourceDevice: Name = "metrics".asName(),
        override val targetDevice: Name? = null,
        val tags: Map<String, String> = emptyMap(),
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : MetricMessage() {
        override fun changeSource(block: (Name) -> Name): MetricMessage = copy(sourceDevice = block(sourceDevice))
    }

    @Serializable
    @SerialName("metrics.gauge")
    public data class Gauge(
        override val name: String,
        val value: Double,
        override val sourceDevice: Name = "metrics".asName(),
        override val targetDevice: Name? = null,
        val tags: Map<String, String> = emptyMap(),
        @EncodeDefault override val time: Instant = Clock.System.now(),
    ) : MetricMessage() {
        override fun changeSource(block: (Name) -> Name): MetricMessage = copy(sourceDevice = block(sourceDevice))
    }
}


public fun DeviceMessage.toMeta(): Meta = Json.encodeToJsonElement(this).toMeta()

public fun DeviceMessage.toEnvelope(): Envelope = Envelope(toMeta(), null)
