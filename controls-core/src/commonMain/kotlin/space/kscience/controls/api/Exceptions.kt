package space.kscience.controls.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Device error categories by severity level.
 * - [CRITICAL] - critical error requiring immediate response
 * - [NON_CRITICAL] - non-critical error that allows continued operation
 */
public enum class DeviceErrorCategory {
    CRITICAL,
    NON_CRITICAL
}

/**
 * Base class for all device-related exceptions.
 */
public sealed class DeviceException(
    message: String,
    cause: Throwable? = null,
    public open val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    public open val context: Map<String, Any?> = emptyMap()
) : RuntimeException(message, cause) {

    /**
     * Creates a new exception instance with updated context
     */
    protected abstract fun withNewContext(newContext: Map<String, Any?>): DeviceException

    /**
     * Adds a key-value pair to the exception context
     */
    public fun withContext(key: String, value: Any?): DeviceException {
        return withNewContext(this.context + (key to value))
    }

    /**
     * Adds multiple pairs to the exception context
     */
    public fun withContext(additionalContext: Map<String, Any?>): DeviceException {
        return withNewContext(this.context + additionalContext)
    }

    /**
     * Converts the exception to a serializable representation for messaging
     */
    public open fun toSerializableFailure(): SerializableDeviceFailure = SerializableDeviceFailure(
        message = this.message ?: "Unknown error",
        type = this::class.simpleName ?: "DeviceException",
        category = this.category,
        context = this.context.mapValues { it.value?.toString() },
        causeType = this.cause?.let { it::class.simpleName },
        causeMessage = this.cause?.message
    )
}

/**
 * Exception for device connection errors
 */
public class DeviceConnectionException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceConnectionException =
        DeviceConnectionException(message ?: "", cause, category, newContext)
}

/**
 * Exception for device operation timeout
 */
public class DeviceTimeoutException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceTimeoutException =
        DeviceTimeoutException(message ?: "", cause, category, newContext)
}

/**
 * Exception for device configuration errors
 */
public class DeviceConfigurationException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceConfigurationException =
        DeviceConfigurationException(message ?: "", cause, category, newContext)
}

/**
 * Exception for concurrent access errors to a device
 */
public class DeviceConcurrencyException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceConcurrencyException =
        DeviceConcurrencyException(message ?: "", cause, category, newContext)
}

/**
 * Exception for device startup errors
 */
public class DeviceStartupException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceStartupException =
        DeviceStartupException(message ?: "", cause, category, newContext)
}

/**
 * Exception for device shutdown errors
 */
public class DeviceShutdownException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceShutdownException =
        DeviceShutdownException(message ?: "", cause, category, newContext)
}

/**
 * Exception for device state transition errors
 */
public class DeviceStateTransitionException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceStateTransitionException =
        DeviceStateTransitionException(message ?: "", cause, category, newContext)
}

/**
 * Exception for device operation errors
 */
public class DeviceOperationException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.NON_CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceOperationException =
        DeviceOperationException(message ?: "", cause, category, newContext)
}

public class DeviceNotFoundException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceNotFoundException =
        DeviceNotFoundException(message ?: "", cause, category, newContext)
}

/**
 * Serializable representation of a device failure.
 * Used to transmit error information between processes.
 */
@Serializable
public data class SerializableDeviceFailure(
    val message: String,
    val type: String,
    val category: DeviceErrorCategory,
    val context: Map<String, @Contextual String?> = emptyMap(),
    val causeType: String? = null,
    val causeMessage: String? = null
)