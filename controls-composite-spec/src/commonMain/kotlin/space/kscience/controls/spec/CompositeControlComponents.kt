@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package space.kscience.controls.spec

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import space.kscience.controls.api.*
import space.kscience.controls.constructor.*
import space.kscience.controls.spec.DeviceErrorCategory.CRITICAL
import space.kscience.controls.spec.DeviceErrorCategory.NON_CRITICAL
import space.kscience.controls.spec.LifecycleMode.INDEPENDENT
import space.kscience.controls.spec.LifecycleMode.LINKED
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.send
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.pow
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

//region Error Handling

/**
 * Categorization of errors by severity:
 * - [CRITICAL] means the error requires strong reaction.
 * - [NON_CRITICAL] means the system can continue with partial functionality or just log a warning.
 */
public enum class DeviceErrorCategory {
    CRITICAL,
    NON_CRITICAL
}

/**
 * Base class for all device-related exceptions
 */
public sealed class DeviceException(
    message: String,
    cause: Throwable? = null,
    public open val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    public open val context: Map<String, Any?> = emptyMap()
) : RuntimeException(message, cause) {

    /**
     * Creates a new instance of this exception with updated context
     */
    protected abstract fun withNewContext(newContext: Map<String, Any?>): DeviceException

    /**
     * Adds a single key-value pair to the exception context
     */
    public fun withContext(key: String, value: Any?): DeviceException {
        val newContext = context + (key to value)
        return withNewContext(newContext)
    }

    /**
     * Adds multiple entries to the exception context
     */
    public fun withContext(additionalContext: Map<String, Any?>): DeviceException {
        val newContext = context + additionalContext
        return withNewContext(newContext)
    }
}

/**
 * Exception thrown when a connection to a device fails
 */
public class DeviceConnectionException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceConnectionException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device operation times out
 */
public class DeviceTimeoutException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceTimeoutException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device configuration is invalid
 */
public class DeviceConfigurationException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceConfigurationException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device operation fails due to concurrent access
 */
public class DeviceConcurrencyException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceConcurrencyException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device operation fails during startup
 */
public class DeviceStartupException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceStartupException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device operation fails during shutdown
 */
public class DeviceShutdownException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceShutdownException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device state transition is invalid
 */
public class DeviceStateTransitionException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceStateTransitionException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown for general device operation failures
 */
public class DeviceOperationException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.NON_CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceOperationException(message ?: "", cause, category, newContext)
    }
}

/**
 * Exception thrown when a device is not found in registry
 */
public class DeviceNotFoundInRegistryException(
    message: String,
    cause: Throwable? = null,
    override val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL,
    override val context: Map<String, Any?> = emptyMap()
) : DeviceException(message, cause, category, context) {
    override fun withNewContext(newContext: Map<String, Any?>): DeviceException {
        return DeviceNotFoundInRegistryException(message ?: "", cause, category, newContext)
    }
}

//endregion

//region Messaging System

/**
 * Extension property to get message type from SerialName annotation
 */
public val Message.messageType: String
    get() = when (this) {
        is PropertyChangedMessage -> "property.changed"
        is PropertySetMessage -> "property.set"
        is PropertyGetMessage -> "property.get"
        is GetDescriptionMessage -> "description.get"
        is DescriptionMessage -> "description"
        is ActionExecuteMessage -> "action.execute"
        is ActionResultMessage -> "action.result"
        is BinaryNotificationMessage -> "binary.notification"
        is EmptyDeviceMessage -> "empty"
        is DeviceLogMessage -> "device.log"
        is SystemLogMessage -> "system.log"
        is DeviceErrorMessage -> "error"
        is DeviceLifeCycleMessage -> "lifecycle"
        is TransactionMessage -> when (this) {
            is TransactionMessage.Started -> "transaction.started"
            is TransactionMessage.Committed -> "transaction.committed"
            is TransactionMessage.RolledBack -> "transaction.rolled_back"
            is TransactionMessage.Savepoint -> "transaction.savepoint"
        }
        is DeviceStateMessage -> when (this) {
            is DeviceStateMessage.Added -> "device.state.added"
            is DeviceStateMessage.Started -> "device.state.started"
            is DeviceStateMessage.Stopped -> "device.state.stopped"
            is DeviceStateMessage.Removed -> "device.state.removed"
            is DeviceStateMessage.Failed -> "device.state.failed"
            is DeviceStateMessage.Detached -> "device.state.detached"
        }
        is MetricMessage -> when (this) {
            is MetricMessage.Value -> "metrics.value"
            is MetricMessage.Counter -> "metrics.counter"
            is MetricMessage.Duration -> "metrics.duration"
            is MetricMessage.Distribution -> "metrics.distribution"
            is MetricMessage.Gauge -> "metrics.gauge"
        }
    }

/**
 * Helper functions for creating common message types
 */
public object MessageFactory {
    /**
     * Creates a device log message
     */
    public fun deviceLog(
        message: String,
        sourceDevice: Name,
        data: Meta? = null
    ): DeviceLogMessage = DeviceLogMessage(
        message = message,
        sourceDevice = sourceDevice,
        data = data
    )

    /**
     * Creates a system log message
     */
    public fun systemLog(
        message: String,
        component: String,
        details: Map<String, String> = emptyMap()
    ): SystemLogMessage = SystemLogMessage(
        message = message,
        component = component,
        details = details
    )

    /**
     * Creates a metric value message
     */
    public fun metric(
        name: String,
        value: Double,
        tags: Map<String, String> = emptyMap()
    ): MetricMessage.Value = MetricMessage.Value(
        name = name,
        value = value,
        tags = tags
    )

    /**
     * Creates a counter increment message
     */
    public fun counter(
        name: String,
        increment: Double = 1.0,
        tags: Map<String, String> = emptyMap()
    ): MetricMessage.Counter = MetricMessage.Counter(
        name = name,
        increment = increment,
        tags = tags
    )

    /**
     * Creates a duration metric message
     */
    public fun duration(
        name: String,
        duration: Duration,
        tags: Map<String, String> = emptyMap()
    ): MetricMessage.Duration = MetricMessage.Duration(
        name = name,
        durationMs = duration.inWholeMilliseconds,
        tags = tags
    )

    /**
     * Creates a device state added message
     */
    public fun deviceAdded(deviceName: String): DeviceStateMessage.Added =
        DeviceStateMessage.Added(deviceName)

    /**
     * Creates a device state started message
     */
    public fun deviceStarted(deviceName: String): DeviceStateMessage.Started =
        DeviceStateMessage.Started(deviceName)

    /**
     * Creates a device state stopped message
     */
    public fun deviceStopped(deviceName: String): DeviceStateMessage.Stopped =
        DeviceStateMessage.Stopped(deviceName)
}

/**
 * Extension functions for Device to easily create logs
 */
public fun Device.log(
    message: String,
    data: Meta? = null
): DeviceLogMessage = DeviceLogMessage(
    message = message,
    sourceDevice = this.id.asName(),
    data = data
)

/**
 * Filter for device messages
 */
@Serializable
public data class MessageFilter(
    val messageType: Collection<String>? = null,
    val sourceDevice: Collection<Name>? = null,
    val targetDevice: Collection<Name?>? = null,
) {
    /**
     * Checks if the message matches this filter
     */
    public fun accepts(message: Message): Boolean =
        messageType?.contains(message.messageType) != false &&
                sourceDevice?.contains(message.sourceDevice) != false &&
                targetDevice?.contains(message.targetDevice) != false

    /**
     * Builder class for creating filters
     */
    public class Builder {
        private val messageTypes = mutableSetOf<String>()
        private val sourceDevices = mutableSetOf<Name>()
        private val targetDevices = mutableSetOf<Name?>()

        public fun messageType(type: String): Builder {
            messageTypes.add(type)
            return this
        }

        public fun sourceDevice(device: Name): Builder {
            sourceDevices.add(device)
            return this
        }

        public fun sourceDevice(deviceName: String): Builder {
            sourceDevices.add(deviceName.parseAsName())
            return this
        }

        public fun targetDevice(device: Name?): Builder {
            targetDevices.add(device)
            return this
        }

        public fun targetDevice(deviceName: String?): Builder {
            targetDevices.add(deviceName?.parseAsName())
            return this
        }

        public fun build(): MessageFilter {
            return MessageFilter(
                messageType = if (messageTypes.isEmpty()) null else messageTypes,
                sourceDevice = if (sourceDevices.isEmpty()) null else sourceDevices,
                targetDevice = if (targetDevices.isEmpty()) null else targetDevices
            )
        }
    }

    public companion object {
        public val ALL: MessageFilter = MessageFilter()

        public fun builder(): Builder = Builder()
    }
}

/**
 * Common interface for message bus implementations.
 * Provides abstraction over different messaging systems.
 */
public interface MessageBus {
    /**
     * Subscribes to messages matching the given filter
     */
    public fun subscribe(filter: MessageFilter): Flow<Message>

    /**
     * Publishes a message to the bus
     */
    public suspend fun publish(message: Message)

    /**
     * Closes the message bus connection
     */
    public fun close()
}

/**
 * Factory for creating message bus instances based on context capabilities
 */
public object MessageBusFactory {
    /**
     * Creates an appropriate message bus based on context
     * Prefers Magix if available, falls back to in-memory implementation
     *
     * @param context Context that may contain a MagixEndpoint
     * @param sourceEndpoint Source endpoint identifier for Magix
     * @param messageBufferSize Buffer size for in-memory implementation
     * @return A MessageBus implementation
     */
    public fun create(
        context: Context,
        sourceEndpoint: String = "device.hub",
        messageBufferSize: Int = 64
    ): MessageBus {
        val magixEndpoint = context.plugins.filterIsInstance<MagixEndpoint>().firstOrNull()
        return magixEndpoint?.let {
            MagixMessageBus(it, sourceEndpoint)
        } ?: InMemoryMessageBus(messageBufferSize)
    }
}

/**
 * Adapter implementing MessageBus with MagixEndpoint
 */
public class MagixMessageBus(
    public val magixEndpoint: MagixEndpoint,
    public val sourceEndpoint: String
) : MessageBus {

    private val messageFormat = MagixFormat(
        Message.serializer(),
        setOf("controls.device.message")
    )

    /**
     * Publishes a payload using a specific format
     */
    public suspend fun <T> publishWithFormat(
        format: MagixFormat<T>,
        payload: T,
        sourceEndpoint: String = this.sourceEndpoint
    ) {
        magixEndpoint.send(
            format,
            payload,
            sourceEndpoint
        )
    }

    override fun subscribe(filter: MessageFilter): Flow<Message> {
        val magixFilter = MagixMessageFilter(
            format = setOf(messageFormat.defaultFormat)
        )

        return flow {
            magixEndpoint.subscribe(magixFilter).collect { magixMessage ->
                try {
                    val payload = magixMessage.payload
                    val message = MagixEndpoint.magixJson.decodeFromJsonElement(
                        Message.serializer(),
                        payload
                    )

                    if (filter.accepts(message)) {
                        emit(message)
                    }
                } catch (_: Exception) {
                    // Silent catch - don't emit malformed messages
                }
            }
        }
    }

    override suspend fun publish(message: Message) {
        magixEndpoint.send(
            messageFormat,
            message,
            sourceEndpoint
        )
    }

    override fun close() {
        // No explicit close needed for magixEndpoint
    }
}

/**
 * Adapter implementing MessageBus in Memory
 */
public class InMemoryMessageBus(private val extraBufferCapacity: Int = 64) : MessageBus {

    private val sharedFlow = MutableSharedFlow<Message>(
        replay = 0,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun subscribe(filter: MessageFilter): Flow<Message> {
        return sharedFlow.filter { message ->
            filter.accepts(message)
        }
    }

    override suspend fun publish(message: Message) {
        sharedFlow.emit(message)
    }

    override fun close() {
        // No resources to release
    }
}

/**
 * Messaging system that provides access to message flows
 */
public class MessagingSystem(
    public val messageBus: MessageBus,
    private val logger: Logger
) {
    /**
     * Publishes a message to the underlying bus
     */
    public suspend fun publish(message: Message) {
        try {
            messageBus.publish(message)
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish message: ${e.message}" }
        }
    }

    /**
     * Gets a flow of specific message types
     */
    public inline fun <reified T : Message> getMessageFlow(): Flow<T> {
        return messageBus.subscribe(MessageFilter.ALL)
            .filterIsInstance<T>()
    }

    /**
     * Gets device log messages
     */
    public fun getDeviceLogMessages(): Flow<DeviceLogMessage> =
        getMessageFlow<DeviceLogMessage>()

    /**
     * Gets system log messages
     */
    public fun getSystemLogMessages(): Flow<SystemLogMessage> =
        getMessageFlow<SystemLogMessage>()

    /**
     * Gets device state events
     */
    public fun getDeviceStateMessages(): Flow<DeviceStateMessage> =
        getMessageFlow<DeviceStateMessage>()

    /**
     * Gets transaction events
     */
    public fun getTransactionMessages(): Flow<TransactionMessage> =
        getMessageFlow<TransactionMessage>()

    /**
     * Gets metric events
     */
    public fun getMetricMessages(): Flow<MetricMessage> =
        getMessageFlow<MetricMessage>()

    /**
     * Gets all messages
     */
    public fun getAllMessages(): Flow<Message> =
        messageBus.subscribe(MessageFilter.ALL)

    /**
     * Publishes a device log message
     */
    public suspend fun logDevice(
        message: String,
        sourceDevice: Name,
        data: Meta? = null
    ) {
        publish(MessageFactory.deviceLog(message, sourceDevice, data))
    }

    /**
     * Publishes a system log message
     */
    public suspend fun logSystem(
        message: String,
        component: String,
        details: Map<String, String> = emptyMap()
    ) {
        publish(MessageFactory.systemLog(message, component, details))
    }

    /**
     * Publishes a metric value message
     */
    public suspend fun recordMetric(
        name: String,
        value: Double,
        tags: Map<String, String> = emptyMap()
    ) {
        publish(MessageFactory.metric(name, value, tags))
    }

    /**
     * Publishes a counter increment message
     */
    public suspend fun incrementCounter(
        name: String,
        increment: Double = 1.0,
        tags: Map<String, String> = emptyMap()
    ) {
        publish(MessageFactory.counter(name, increment, tags))
    }

    /**
     * Publishes a duration metric message
     */
    public suspend fun recordDuration(
        name: String,
        duration: Duration,
        tags: Map<String, String> = emptyMap()
    ) {
        publish(MessageFactory.duration(name, duration, tags))
    }
}

//endregion

//region Metrics and Monitoring

/**
 * Interface for publishing system metrics.
 * Provides an API for different monitoring systems.
 */
public interface MetricsPublisher {
    /**
     * Publishes a metric with the specified name and value.
     *
     * @param name Metric name.
     * @param value Numeric value of the metric.
     * @param tags Additional tags for the metric.
     */
    public suspend fun publishMetric(name: String, value: Double, tags: Map<String, String> = emptyMap())

    /**
     * Records a duration metric.
     *
     * @param name Metric name.
     * @param duration Duration to record.
     * @param tags Additional tags for the metric.
     */
    public suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String> = emptyMap())

    /**
     * Increments a counter.
     *
     * @param name Metric name.
     * @param amount Amount to increment by (default 1.0).
     * @param tags Additional tags for the metric.
     */
    public suspend fun incrementCounter(name: String, amount: Double = 1.0, tags: Map<String, String> = emptyMap())

    /**
     * Records a gauge metric.
     *
     * @param name Metric name.
     * @param value Value to record.
     * @param tags Additional tags for the metric.
     */
    public suspend fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap())

    /**
     * Records a distribution metric.
     *
     * @param name Metric name.
     * @param value Value to add to the distribution.
     * @param tags Additional tags for the metric.
     */
    public suspend fun recordDistribution(name: String, value: Double, tags: Map<String, String> = emptyMap())

    /**
     * Records a histogram bucket value
     *
     * @param name Metric name
     * @param value Value to record
     * @param bucket Bucket name/boundary
     * @param tags Additional tags for the metric
     */
    public suspend fun recordHistogramBucket(name: String, value: Double, bucket: String, tags: Map<String, String> = emptyMap())

    /**
     * Closes the metrics publisher.
     */
    public fun close()
}

/**
 * Implementation of MetricsPublisher using MessagingSystem
 */
public class MetricsPublisherImpl(
    private val messagingSystem: MessagingSystem,
    private val sourceDevice: Name = "metrics".asName(),
    private val logger: Logger
) : MetricsPublisher {

    private val isActive = atomic(true)

    override suspend fun publishMetric(name: String, value: Double, tags: Map<String, String>) {
        if (!isActive.value) {
            logger.warn { "Metrics publisher is closed, not publishing metric $name" }
            return
        }

        try {
            messagingSystem.publish(MetricMessage.Value(name, value, sourceDevice, tags = tags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish metric $name = $value" }
        }
    }

    override suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String>) {
        if (!isActive.value) return

        try {
            messagingSystem.publish(MetricMessage.Duration(name, duration.inWholeMilliseconds, sourceDevice, tags = tags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to record duration $name = $duration" }
        }
    }

    override suspend fun incrementCounter(name: String, amount: Double, tags: Map<String, String>) {
        if (!isActive.value) return

        try {
            messagingSystem.publish(MetricMessage.Counter(name, amount, sourceDevice, tags = tags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to increment counter $name by $amount" }
        }
    }

    override suspend fun recordGauge(name: String, value: Double, tags: Map<String, String>) {
        if (!isActive.value) return

        try {
            messagingSystem.publish(MetricMessage.Gauge(name, value, sourceDevice, tags = tags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to record gauge $name = $value" }
        }
    }

    override suspend fun recordDistribution(name: String, value: Double, tags: Map<String, String>) {
        if (!isActive.value) return

        try {
            messagingSystem.publish(MetricMessage.Distribution(name, value, sourceDevice, tags = tags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to record distribution $name = $value" }
        }
    }

    override suspend fun recordHistogramBucket(name: String, value: Double, bucket: String, tags: Map<String, String>) {
        if (!isActive.value) return

        try {
            val combinedTags = tags + ("bucket" to bucket)
            messagingSystem.publish(MetricMessage.Value("${name}_bucket", value, sourceDevice, tags = combinedTags))
        } catch (e: Exception) {
            logger.error(e) { "Failed to record histogram bucket $name:$bucket = $value" }
        }
    }

    override fun close() {
        isActive.value = false
    }
}

//endregion

//region Device Lifecycle Management

/**
 * Enum defining how a child device's lifecycle is coupled to its parent.
 *
 * - [LINKED] Child starts/stops with the parent.
 * - [INDEPENDENT] Child must be manually started/stopped, independent of parent lifecycle.
 */
public enum class LifecycleMode {
    LINKED,
    INDEPENDENT
}

/**
 * Enum defining how a device should be started when attached to a manager.
 */
public enum class StartMode {
    /**
     * Do not start the device at all; just attach/register it in the manager.
     */
    NONE,

    /**
     * Start the device asynchronously (do not wait for completion of [device.start()]).
     * If [DeviceLifecycleConfig.lifecycleMode] = INDEPENDENT, no start will be performed anyway.
     */
    ASYNC,

    /**
     * Start the device synchronously, waiting up to [DeviceLifecycleConfig.startTimeout].
     * If [DeviceLifecycleConfig.lifecycleMode] = INDEPENDENT, no auto-start is performed.
     */
    SYNC
}

/**
 * Functional interface for performing health checks on a [Device].
 */
public fun interface HealthChecker {
    /**
     * Checks if the given [device] is healthy.
     *
     * @param device The device to check.
     * @return true if healthy, false otherwise.
     */
    public suspend fun isHealthy(device: Device): Boolean
}

/**
 * Health report for a device.
 */
public data class HealthReport(
    val isHealthy: Boolean,
    val metrics: Map<String, Double> = emptyMap(),
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Health checker with reports.
 */
public interface HealthCheckerImpl : HealthChecker {
    public suspend fun getHealthReport(device: Device): HealthReport

    override suspend fun isHealthy(device: Device): Boolean =
        getHealthReport(device).isHealthy
}

/**
 * Enum defining strategies for handling errors in child devices.
 */
public enum class ChildDeviceErrorHandler {
    /** Ignore errors, logging them but continuing operation. */
    IGNORE,

    /** Restart the child device on error, using [RestartPolicy] for configuration. */
    RESTART,

    /** Stop the parent device if a child fails. */
    STOP_PARENT,

    /** Propagate the error upward, potentially cancelling the parent coroutine. */
    PROPAGATE
}

/**
 * Circuit Breaker configuration for resilient failure recovery
 */
public data class CircuitBreakerConfig(
    /** Number of consecutive failures before opening the circuit */
    val failureThreshold: Int = 5,
    /** Time in open state before automatic reset */
    val resetTimeout: Duration = 60.seconds,
    /** Additional time added after each consecutive failure */
    val additionalTimeAfterFailure: Duration = 30.seconds
)

/**
 * Circuit Breaker state tracking
 *
 * @property failureCount Current number of consecutive failures
 * @property openSince Timestamp when the circuit was opened (0 if closed)
 * @property isOpen Whether the circuit is currently open
 * @property config Circuit breaker configuration
 * @property lastAccessed Timestamp of last access for cleanup purposes
 */
private data class CircuitBreakerState(
    var failureCount: Int = 0,
    var openSince: Long = 0,
    var isOpen: Boolean = false,
    val config: CircuitBreakerConfig,
    var lastAccessed: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Enum defining how delays are calculated for restart attempts.
 */
public enum class RestartStrategy {
    /** Fixed delay using [RestartPolicy.delayBetweenAttempts]. */
    LINEAR,

    /** Exponential backoff, e.g., delay * 2^(attempt-1). */
    EXPONENTIAL_BACKOFF,

    /** Fibonacci sequence delay: delayBetweenAttempts * F(n), where F(n) is the nth Fibonacci number */
    FIBONACCI
}

/**
 * Data class describing restart behavior when [ChildDeviceErrorHandler.RESTART] is used.
 *
 * @property maxAttempts Maximum number of restart attempts.
 * @property delayBetweenAttempts Base delay between restart attempts.
 * @property resetOnSuccess Whether to reset the attempt counter on successful start.
 * @property strategy The [RestartStrategy] for calculating delay.
 * @property circuitBreaker Optional circuit breaker configuration
 */
public data class RestartPolicy(
    val maxAttempts: Int = Int.MAX_VALUE,
    val delayBetweenAttempts: Duration = Duration.ZERO,
    val resetOnSuccess: Boolean = true,
    val strategy: RestartStrategy = RestartStrategy.LINEAR,
    val circuitBreaker: CircuitBreakerConfig? = null
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(!delayBetweenAttempts.isNegative()) { "delayBetweenAttempts must not be negative" }
    }

    public companion object {
        /** Default policy: 5 attempts, 2-second delay, linear strategy, reset on success. */
        public val DEFAULT: RestartPolicy = RestartPolicy(
            maxAttempts = 5,
            delayBetweenAttempts = 2.seconds,
            resetOnSuccess = true,
            strategy = RestartStrategy.LINEAR
        )

        public val WITH_CIRCUIT_BREAKER: RestartPolicy = RestartPolicy(
            maxAttempts = 3,
            delayBetweenAttempts = 5.seconds,
            resetOnSuccess = true,
            strategy = RestartStrategy.EXPONENTIAL_BACKOFF,
            circuitBreaker = CircuitBreakerConfig(
                failureThreshold = 3,
                resetTimeout = 30.seconds
            )
        )

        public val FIBONACCI_WITH_CIRCUIT_BREAKER: RestartPolicy = RestartPolicy(
            maxAttempts = 8,
            delayBetweenAttempts = 1.seconds,
            resetOnSuccess = true,
            strategy = RestartStrategy.FIBONACCI,
            circuitBreaker = CircuitBreakerConfig(
                failureThreshold = 5,
                resetTimeout = 2.minutes
            )
        )
    }
}

/**
 * Configuration for a device's lifecycle, including timeouts and error handling.
 *
 * @param lifecycleMode The [LifecycleMode] of the device.
 * @param messageBuffer Buffer size for the device's message flow.
 * @param startDelay Delay before starting the device.
 * @param startTimeout Timeout for starting the device.
 * @param stopTimeout Timeout for stopping the device.
 * @param coroutineScope Optional [CoroutineScope] for the device.
 * @param dispatcher Optional [CoroutineDispatcher] for concurrency.
 * @param onError The [ChildDeviceErrorHandler] strategy.
 * @param healthChecker Optional [HealthChecker] for the device.
 * @param restartPolicy The [RestartPolicy] for [ChildDeviceErrorHandler.RESTART].
 */
public data class DeviceLifecycleConfig(
    val lifecycleMode: LifecycleMode = LifecycleMode.LINKED,
    val messageBuffer: Int = 1000,
    val startDelay: Duration = Duration.ZERO,
    val startTimeout: Duration? = 30.seconds,
    val stopTimeout: Duration? = 10.seconds,
    val coroutineScope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher? = null,
    val onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART,
    val healthChecker: HealthChecker? = null,
    val restartPolicy: RestartPolicy = RestartPolicy.DEFAULT
) {
    init {
        if (messageBuffer <= 0) {
            throw DeviceConfigurationException("Message buffer size must be positive.")
        }
        startTimeout?.let {
            if (it.isNegative()) {
                throw DeviceConfigurationException("Start timeout must be non-negative.")
            }
        }
        stopTimeout?.let {
            if (it.isNegative()) {
                throw DeviceConfigurationException("Stop timeout must be non-negative.")
            }
        }
    }
}

/**
 * Builder class for constructing [DeviceLifecycleConfig] instances.
 */
public class DeviceLifecycleConfigBuilder {
    public var lifecycleMode: LifecycleMode = LifecycleMode.LINKED
    public var messageBuffer: Int = 1000
    public var startDelay: Duration = Duration.ZERO
    public var startTimeout: Duration? = 30.seconds
    public var stopTimeout: Duration? = 10.seconds
    public var coroutineScope: CoroutineScope? = null
    public var dispatcher: CoroutineDispatcher? = null
    public var onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART
    public var healthChecker: HealthChecker? = null
    public var restartPolicy: RestartPolicy = RestartPolicy.DEFAULT

    /** Method to set linkedMode */
    public fun linkedMode(): DeviceLifecycleConfigBuilder {
        lifecycleMode = LifecycleMode.LINKED
        return this
    }

    /** Method to set independentMode */
    public fun independentMode(): DeviceLifecycleConfigBuilder {
        lifecycleMode = LifecycleMode.INDEPENDENT
        return this
    }

    /** Method to set messageBuffer */
    public fun withMessageBuffer(size: Int): DeviceLifecycleConfigBuilder {
        messageBuffer = size
        return this
    }

    /** Method to set startDelay */
    public fun withStartDelay(delay: Duration): DeviceLifecycleConfigBuilder {
        startDelay = delay
        return this
    }

    /** Method to set both startTimeout and stopTimeout */
    public fun withTimeouts(timeout: Duration): DeviceLifecycleConfigBuilder {
        startTimeout = timeout
        stopTimeout = timeout
        return this
    }

    /** Method to set startTimeout */
    public fun withStartTimeout(timeout: Duration?): DeviceLifecycleConfigBuilder {
        startTimeout = timeout
        return this
    }

    /** Method to set stopTimeout */
    public fun withStopTimeout(timeout: Duration?): DeviceLifecycleConfigBuilder {
        stopTimeout = timeout
        return this
    }

    /** Method to set coroutineScope */
    public fun withCoroutineScope(scope: CoroutineScope): DeviceLifecycleConfigBuilder {
        coroutineScope = scope
        return this
    }

    /** Method to set dispatcher */
    public fun withDispatcher(disp: CoroutineDispatcher): DeviceLifecycleConfigBuilder {
        dispatcher = disp
        return this
    }

    /** Method to set onError to IGNORE */
    public fun ignoreErrors(): DeviceLifecycleConfigBuilder {
        onError = ChildDeviceErrorHandler.IGNORE
        return this
    }

    /** Method to set onError to RESTART */
    public fun restartOnError(policy: RestartPolicy = RestartPolicy.DEFAULT): DeviceLifecycleConfigBuilder {
        onError = ChildDeviceErrorHandler.RESTART
        restartPolicy = policy
        return this
    }

    /** Method to set onError to STOP_PARENT */
    public fun stopParentOnError(): DeviceLifecycleConfigBuilder {
        onError = ChildDeviceErrorHandler.STOP_PARENT
        return this
    }

    /** Method to set onError to PROPAGATE */
    public fun propagateErrors(): DeviceLifecycleConfigBuilder {
        onError = ChildDeviceErrorHandler.PROPAGATE
        return this
    }

    /** Method to set healthChecker */
    public fun withHealthChecker(checker: HealthChecker): DeviceLifecycleConfigBuilder {
        healthChecker = checker
        return this
    }

    /** Method to set restartPolicy */
    public fun withRestartPolicy(policy: RestartPolicy): DeviceLifecycleConfigBuilder {
        restartPolicy = policy
        return this
    }

    /** Builds and returns the [DeviceLifecycleConfig]. */
    public fun build(): DeviceLifecycleConfig = DeviceLifecycleConfig(
        lifecycleMode = lifecycleMode,
        messageBuffer = messageBuffer,
        startDelay = startDelay,
        startTimeout = startTimeout,
        stopTimeout = stopTimeout,
        coroutineScope = coroutineScope,
        dispatcher = dispatcher,
        onError = onError,
        healthChecker = healthChecker,
        restartPolicy = restartPolicy
    )
}

//endregion

//region Transactions

/**
 * Interface for a reversible action that can be undone during a transaction rollback
 */
public interface ReversibleAction {
    /**
     * Unique identifier for this action
     */
    public val id: String

    /**
     * Undoes the action during transaction rollback
     */
    public suspend fun reverse()
}

/**
 * Represents a transaction context with a unique ID.
 * This is used to track and manage transactions.
 */
public class TransactionContext(
    public val id: String,
    private val actions: MutableList<ReversibleAction> = mutableListOf(),
    public val startTime: Long = Clock.System.now().toEpochMilliseconds()
) {
    private val mutex = Mutex()
    private val savepoints = mutableMapOf<String, Int>()

    /**
     * Records an action in this transaction.
     *
     * @param action The action to record.
     */
    public suspend fun recordAction(action: ReversibleAction) {
        mutex.withLock {
            actions.add(action)
        }
    }

    /**
     * Creates a savepoint that can be used for partial rollbacks.
     *
     * @param name Unique name for the savepoint
     * @return The savepoint name
     */
    public suspend fun createSavepoint(name: String): String {
        mutex.withLock {
            savepoints[name] = actions.size
        }
        return name
    }

    /**
     * Rolls back to a specific savepoint, undoing actions
     * performed after that savepoint was created.
     *
     * @param name The savepoint name to roll back to
     * @throws IllegalArgumentException if the savepoint doesn't exist
     */
    public suspend fun rollbackToSavepoint(name: String) {
        val actionsToRollback = mutex.withLock {
            val index = savepoints[name] ?: throw IllegalArgumentException("Savepoint $name not found")
            val rollbackList = actions.subList(index, actions.size).toList().asReversed()
            actions.subList(index, actions.size).clear()
            rollbackList
        }

        for (action in actionsToRollback) {
            action.reverse()
        }
    }

    /**
     * Gets the list of actions that have been recorded in this transaction.
     *
     * @return A list of [ReversibleAction] instances.
     */
    public suspend fun getActions(): List<ReversibleAction> = mutex.withLock {
        actions.toList()
    }
}

/**
 * Element for storing transaction context in coroutine context
 */
private class TransactionContextElement(val context: TransactionContext) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TransactionContextElement>

    override val key: CoroutineContext.Key<TransactionContextElement> = Key
}

/**
 * Interface for managing transactional operations.
 */
public interface TransactionManager {
    /**
     * Executes a [block] within a transaction, rolling back on failure.
     *
     * @param block The suspend function representing the transactional block.
     * @return The result of the block execution.
     * @throws Exception if the transaction fails, after publishing rollback events.
     */
    public suspend fun <T> withTransaction(block: suspend (TransactionContext) -> T): T

    /**
     * Records a reversible action as part of the current transaction.
     *
     * @param action The action to record.
     * @throws IllegalStateException if not in a transaction.
     */
    public suspend fun recordAction(action: ReversibleAction)

    /**
     * Checks if the calling context is currently in a transaction.
     *
     * @return True if in a transaction, false otherwise.
     */
    public suspend fun isInTransaction(): Boolean

    /**
     * Creates a savepoint in the current transaction.
     *
     * @param name Unique name for the savepoint
     * @return The savepoint name
     */
    public suspend fun createSavepoint(name: String): String

    /**
     * Rolls back to a specific savepoint
     *
     * @param name The savepoint name
     */
    public suspend fun rollbackToSavepoint(name: String)
}

/**
 * Implementation of TransactionManager that uses MessagingSystem for event publishing
 */
public class TransactionManagerImpl(
    private val messagingSystem: MessagingSystem,
    private val logger: Logger = DefaultLogManager()
) : TransactionManager {
    private val transactionLock = Mutex()
    private val activeTransactions = mutableMapOf<String, TransactionContext>()

    override suspend fun <T> withTransaction(block: suspend (TransactionContext) -> T): T {
        val currentContext = coroutineContext

        val existingTx = currentContext[TransactionContextElement.Key]
        if (existingTx != null) {
            return block(existingTx.context)
        }

        val txId = generateTransactionId()
        val txContext = TransactionContext(txId)

        try {
            transactionLock.withLock {
                activeTransactions[txId] = txContext
            }

            val contextWithTransaction = currentContext + TransactionContextElement(txContext)
            messagingSystem.publish(TransactionMessage.Started(txId))
            logger.info { "Transaction $txId started" }

            val result = withContext(contextWithTransaction) {
                block(txContext)
            }

            messagingSystem.publish(TransactionMessage.Committed(txId))
            logger.info { "Transaction $txId committed" }
            return result
        } catch (ex: Exception) {
            logger.error(ex) { "Transaction $txId failed, rolling back." }

            val actions = txContext.getActions()
            var rollbackError: Exception? = null

            for (action in actions.reversed()) {
                try {
                    action.reverse()
                } catch (undoEx: Exception) {
                    logger.error(undoEx) { "Failed to reverse action ${action.id} during rollback of transaction $txId" }

                    val wrappedError = Exception("Failed to reverse action ${action.id}", undoEx)
                    if (rollbackError == null) {
                        rollbackError = wrappedError
                    } else {
                        rollbackError.addSuppressed(wrappedError)
                    }
                }
            }

            messagingSystem.publish(
                TransactionMessage.RolledBack(
                    txId,
                    ex.message,
                    ex::class.simpleName
                )
            )

            if (rollbackError != null) {
                ex.addSuppressed(Exception("Errors during rollback", rollbackError))
            }

            throw ex
        } finally {
            transactionLock.withLock {
                activeTransactions.remove(txId)
            }
        }
    }

    override suspend fun recordAction(action: ReversibleAction) {
        val contextElement = coroutineContext[TransactionContextElement.Key]
            ?: throw IllegalStateException("No active transaction in current context")

        contextElement.context.recordAction(action)
    }

    override suspend fun isInTransaction(): Boolean {
        return coroutineContext[TransactionContextElement.Key] != null
    }

    override suspend fun createSavepoint(name: String): String {
        val contextElement = coroutineContext[TransactionContextElement.Key]
            ?: throw IllegalStateException("No active transaction in current context")

        val txId = contextElement.context.id
        val savepointId = contextElement.context.createSavepoint(name)

        messagingSystem.publish(
            TransactionMessage.Savepoint(
                txId,
                savepointId
            )
        )

        return savepointId
    }

    override suspend fun rollbackToSavepoint(name: String) {
        val contextElement = coroutineContext[TransactionContextElement.Key]
            ?: throw IllegalStateException("No active transaction in current context")

        contextElement.context.rollbackToSavepoint(name)
    }

    private fun generateTransactionId(): String = "tx_${Clock.System.now().toEpochMilliseconds()}"
}

//endregion

//region Device Registry and Hub Management

/**
 * DeviceManager configuration plugin for the context
 */
public class DeviceManagerConfig(
    public val messageBufferSize: Int = 1000,
    public val defaultConcurrencyLevel: Int = 4,
    public val defaultStartTimeout: Duration = 30.seconds,
    public val defaultStopTimeout: Duration = 10.seconds,
    public val resourceCleanupInterval: Duration = 15.minutes,
    public val resourceMaxIdleTime: Duration = 60.minutes
) : AbstractPlugin() {
    override val tag: PluginTag get() = Companion.tag

    init {
        require(messageBufferSize > 0) { "Message buffer size must be positive" }
        require(defaultConcurrencyLevel > 0) { "Concurrency level must be positive" }
        require(!resourceCleanupInterval.isNegative()) { "Resource cleanup interval must not be negative" }
        require(!resourceMaxIdleTime.isNegative()) { "Resource max idle time must not be negative" }
    }

    public companion object : PluginFactory<DeviceManagerConfig> {
        override val tag: PluginTag = PluginTag("controls.device.config", PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): DeviceManagerConfig {
            val messageBuffer = meta["messageBufferSize"].int ?: 1000
            val concurrencyLevel = meta["defaultConcurrencyLevel"].int ?: 4
            val startTimeout = meta["defaultStartTimeout"]?.string?.let { Duration.parse(it) } ?: 30.seconds
            val stopTimeout = meta["defaultStopTimeout"]?.string?.let { Duration.parse(it) } ?: 10.seconds
            val cleanupInterval = meta["resourceCleanupInterval"]?.string?.let { Duration.parse(it) } ?: 15.minutes
            val maxIdleTime = meta["resourceMaxIdleTime"]?.string?.let { Duration.parse(it) } ?: 60.minutes

            return DeviceManagerConfig(
                messageBufferSize = messageBuffer,
                defaultConcurrencyLevel = concurrencyLevel,
                defaultStartTimeout = startTimeout,
                defaultStopTimeout = stopTimeout,
                resourceCleanupInterval = cleanupInterval,
                resourceMaxIdleTime = maxIdleTime
            )
        }
    }
}

/**
 * Extension to get DeviceManagerConfig from context
 */
public val Context.deviceManagerConfig: DeviceManagerConfig
    get() = plugins[DeviceManagerConfig] ?: DeviceManagerConfig()

/**
 * Provides information about system resources
 */
public class SystemResourceInfo(
    private val context: Context
) {
    public fun getConcurrencyLevel(): Int = context.deviceManagerConfig.defaultConcurrencyLevel
}

/**
 * Registry for managing device lifecycle and operations.
 * Maintains a registry of devices and handles their lifecycle events.
 */
public class DeviceRegistry(
    private val context: Context
) {
    private val childLock = Mutex()
    private val childrenJobs = mutableMapOf<Name, DeviceJob>()

    /**
     * Represents a registered device with its configuration and job.
     */
    public data class DeviceJob(
        val device: Device,
        val collectorJob: Job,
        val config: DeviceLifecycleConfig,
        val meta: Meta? = null
    ) {
        val lifecycleMode: LifecycleMode get() = config.lifecycleMode
    }

    /**
     * Snapshot of current devices (not guaranteed to be consistent without a lock).
     * For a safe snapshot, use [getDevicesSafe].
     */
    public val devices: Map<Name, Device>
        get() = childrenJobs.mapValues { it.value.device }

    /**
     * Returns a safe snapshot of current devices by locking [childLock].
     *
     * @return A [Map] of device names to devices.
     */
    public suspend fun getDevicesSafe(): Map<Name, Device> = childLock.withLock {
        childrenJobs.mapValues { it.value.device }
    }

    /**
     * Registers a device in the registry.
     *
     * @param name The name of the device.
     * @param device The device instance.
     * @param config The lifecycle configuration.
     * @param meta Optional metadata.
     * @param messageHandler Handler for device messages
     * @return The [DeviceJob] that was created.
     */
    public suspend fun registerDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        messageHandler: suspend (Message) -> Unit
    ): DeviceJob = childLock.withLock {
        val scope = config.coroutineScope ?: CoroutineScope(
            config.dispatcher ?: (Dispatchers.Default +
                    SupervisorJob() +
                    CoroutineName("Device-$name"))
        )

        val collectorJob = scope.launch(CoroutineName("Collect device $name")) {
            try {
                device.messageFlow.collect { msg ->
                    val wrapped = msg.changeSource { name.plus(it) }
                    messageHandler(wrapped)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                context.logger.error(ex) { "Error collecting messages from device $name" }
                throw ex
            }
        }

        val deviceJob = DeviceJob(
            device = device,
            collectorJob = collectorJob,
            config = config,
            meta = meta
        )

        childrenJobs[name] = deviceJob
        return deviceJob
    }

    /**
     * Gets a device job by name.
     *
     * @param name The name of the device.
     * @return The [DeviceJob] or null if not found.
     */
    public suspend fun getDeviceJob(name: Name): DeviceJob? = childLock.withLock {
        return childrenJobs[name]
    }

    /**
     * Removes a device from the registry.
     *
     * @param name The name of the device.
     * @return The removed [DeviceJob] or null if not found.
     */
    public suspend fun removeDevice(name: Name): DeviceJob? = childLock.withLock {
        return childrenJobs.remove(name)
    }

    /**
     * Updates a device in the registry.
     *
     * @param name The name of the device.
     * @param job The new [DeviceJob].
     */
    public suspend fun updateDevice(name: Name, job: DeviceJob): Unit = childLock.withLock {
        childrenJobs[name] = job
    }

    /**
     * Checks if a device with the given name exists in the registry.
     *
     * @param name The name of the device.
     * @return True if the device exists, false otherwise.
     */
    public suspend fun containsDevice(name: Name): Boolean = childLock.withLock {
        return childrenJobs.containsKey(name)
    }

    /**
     * Gets the names of all devices in the registry.
     *
     * @return A set of device names.
     */
    public suspend fun getDeviceNames(): Set<Name> = childLock.withLock {
        return childrenJobs.keys.toSet()
    }

    /**
     * Clears all devices from the registry.
     * Note: This does not stop the devices or cancel their jobs.
     */
    public suspend fun clear(): Unit = childLock.withLock {
        childrenJobs.clear()
    }
}

/**
 * Manages the lifecycle of devices (start, stop, restart operations)
 */
public class DeviceLifecycleManager(
    private val context: Context,
    private val registry: DeviceRegistry,
    private val messagingSystem: MessagingSystem,
    private val logger: Logger = context.logger
) {

    /**
     * Attaches a device to the registry with optional auto-start
     */
    public suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        startMode: StartMode = StartMode.NONE
    ) {
        if (registry.containsDevice(name)) {
            throw DeviceConfigurationException("Device with name $name already exists")
        }

        val deviceJob = registry.registerDevice(
            name = name,
            device = device,
            config = config,
            meta = meta
        ) { message ->
            messagingSystem.publish(message)
        }

        // Publish device added event
        messagingSystem.publish(DeviceStateMessage.Added(name.toString()))
        logger.info { "Device $name attached with startMode=$startMode" }

        if (config.lifecycleMode == LifecycleMode.INDEPENDENT) return

        when (startMode) {
            StartMode.NONE -> Unit
            StartMode.ASYNC -> context.launch { startDevice(name, config, device) }
            StartMode.SYNC -> startDevice(name, config, device)
        }
    }

    /**
     * Starts a device
     */
    public suspend fun startDevice(name: Name, config: DeviceLifecycleConfig? = null, device: Device? = null) {
        val configToUse = config ?: registry.getDeviceJob(name)?.config
        ?: throw DeviceConfigurationException("Device $name not found")
        val deviceToUse = device ?: registry.getDeviceJob(name)?.device
        ?: throw DeviceConfigurationException("Device $name not found")

        val state = deviceToUse.lifecycleState
        if (state == LifecycleState.STARTED) {
            logger.warn { "Device $name is already started" }
            return
        }

        if (state != LifecycleState.INITIAL && state != LifecycleState.STOPPED) {
            logger.warn { "Cannot start device $name because it is in state $state" }
            return
        }

        if (configToUse.startDelay > Duration.ZERO) delay(configToUse.startDelay)

        val startTime = Clock.System.now()
        try {
            val startTimeout = configToUse.startTimeout ?: context.deviceManagerConfig.defaultStartTimeout

            withTimeout(startTimeout) {
                deviceToUse.start()
            }

            val duration = Clock.System.now() - startTime
            messagingSystem.recordDuration("device.start.duration", duration,
                mapOf("device" to name.toString()))

            messagingSystem.publish(DeviceStateMessage.Started(name.toString()))
            logger.info { "Device $name started in $duration" }
        } catch (e: TimeoutCancellationException) {
            messagingSystem.incrementCounter("device.start.timeout", 1.0,
                mapOf("device" to name.toString()))
            logger.error { "Timeout starting device $name" }
            throw DeviceTimeoutException("Timeout while starting device $name", e)
        } catch (e: Exception) {
            messagingSystem.incrementCounter("device.start.error", 1.0,
                mapOf("device" to name.toString(), "error" to e::class.simpleName.toString()))
            logger.error(e) { "Error starting device $name" }
            messagingSystem.publish(DeviceStateMessage.Failed(
                name.toString(),
                e.message ?: "Failed to start device",
                e::class.simpleName
            ))
            throw DeviceStartupException("Failed to start device $name", e)
        }
    }

    /**
     * Detaches a device from the registry
     */
    public suspend fun detachDevice(name: Name, waitStop: Boolean = false) {
        val deviceJob = registry.removeDevice(name)

        if (deviceJob != null) {
            messagingSystem.publish(DeviceStateMessage.Removed(name.toString()))
            logger.info { "Device $name removed (waitStop=$waitStop)" }

            if (waitStop) {
                stopDevice(name, deviceJob)
            } else {
                context.launch { stopDevice(name, deviceJob) }
            }
        }
    }

    /**
     * Stops a device
     */
    public suspend fun stopDevice(name: Name, deviceJob: DeviceRegistry.DeviceJob? = null) {
        val job = deviceJob ?: registry.getDeviceJob(name)
        ?: throw DeviceConfigurationException("Device $name not found")

        val timeout = job.config.stopTimeout ?: context.deviceManagerConfig.defaultStopTimeout

        // Validate state transition
        val state = job.device.lifecycleState
        if (state != LifecycleState.STARTED) {
            logger.warn { "Device $name is not in STARTED state (current: $state)" }
            return
        }

        val startTime = Clock.System.now()

        try {
            withTimeout(timeout) {
                job.device.stop()
            }

            val duration = Clock.System.now() - startTime
            messagingSystem.recordDuration("device.stop.duration", duration,
                mapOf("device" to name.toString()))
            logger.info { "Device $name stopped in $duration" }

        } catch (_: TimeoutCancellationException) {
            messagingSystem.incrementCounter("device.stop.timeout", 1.0,
                mapOf("device" to name.toString()))
            logger.warn { "Timeout stopping device $name" }
        } catch (e: Exception) {
            messagingSystem.incrementCounter("device.stop.error", 1.0,
                mapOf("device" to name.toString(), "error" to e::class.simpleName.toString()))
            logger.error(e) { "Error stopping device $name" }
            throw DeviceShutdownException("Failed to stop device $name", e)
        } finally {
            withContext(NonCancellable) {
                try {
                    job.collectorJob.cancelAndJoin()
                } catch (e: Exception) {
                    logger.error(e) { "Error cancelling collector job for device $name" }
                }
            }
        }

        messagingSystem.publish(DeviceStateMessage.Stopped(name.toString()))
    }

    private val fibonacciCache = mutableMapOf<Int, Int>().apply {
        put(1, 1)
        put(2, 1)
    }

    private fun fibonacci(n: Int): Int {
        return fibonacciCache.getOrPut(n) { fibonacci(n - 1) + fibonacci(n - 2) }
    }

    /**
     * Calculates restart delay based on policy and attempts
     */
    internal fun calculateRestartDelay(policy: RestartPolicy, attempts: Int): Duration {
        return when (policy.strategy) {
            RestartStrategy.LINEAR ->
                policy.delayBetweenAttempts
            RestartStrategy.EXPONENTIAL_BACKOFF -> {
                val multiplier = 2.0.pow((attempts - 1).toDouble())
                policy.delayBetweenAttempts.times(multiplier)
            }
            RestartStrategy.FIBONACCI -> {
                policy.delayBetweenAttempts.times(fibonacci(attempts).toDouble())
            }
        }
    }
}

/**
 * Manages circuit breaker patterns for device fault tolerance
 */
public class CircuitBreakerManager(
    private val context: Context,
    private val messagingSystem: MessagingSystem,
    private val logger: Logger = context.logger
) {
    private val circuitBreakerStates = mutableMapOf<Name, CircuitBreakerState>()
    private val lock = Mutex()

    /**
     * Checks if a device can be restarted based on circuit breaker state
     *
     * @param deviceName Name of the device
     * @param policy Restart policy with optional circuit breaker config
     * @return true if restart can be attempted, false if circuit breaker is open
     */
    public suspend fun shouldAttemptRestart(deviceName: Name, policy: RestartPolicy): Boolean {
        val circuitBreakerConfig = policy.circuitBreaker ?: return true

        return lock.withLock {
            val state = circuitBreakerStates.getOrPut(deviceName) {
                CircuitBreakerState(config = circuitBreakerConfig)
            }
            state.lastAccessed = Clock.System.now().toEpochMilliseconds()

            if (state.isOpen) {
                val now = Clock.System.now().toEpochMilliseconds()
                val timeInOpenState = now - state.openSince

                val resetTimeoutMs = circuitBreakerConfig.resetTimeout.inWholeMilliseconds +
                        (state.failureCount - circuitBreakerConfig.failureThreshold).coerceAtLeast(0) *
                        circuitBreakerConfig.additionalTimeAfterFailure.inWholeMilliseconds

                if (timeInOpenState > resetTimeoutMs) {
                    state.isOpen = false
                    state.failureCount = 0
                    messagingSystem.incrementCounter("device.circuit_breaker.auto_reset",
                        tags = mapOf("device" to deviceName.toString()))
                    logger.info { "Circuit breaker for device $deviceName automatically reset after timeout" }
                    true
                } else {
                    messagingSystem.incrementCounter("device.circuit_breaker.reject",
                        tags = mapOf("device" to deviceName.toString()))
                    logger.debug { "Circuit breaker for device $deviceName is open, rejecting restart" }
                    false
                }
            } else {
                true
            }
        }
    }

    /**
     * Records a restart failure and updates circuit breaker state
     *
     * @param deviceName Name of the device
     * @param policy Restart policy with optional circuit breaker config
     */
    public suspend fun recordRestartFailure(deviceName: Name, policy: RestartPolicy) {
        val circuitBreakerConfig = policy.circuitBreaker ?: return

        lock.withLock {
            val state = circuitBreakerStates.getOrPut(deviceName) {
                CircuitBreakerState(config = circuitBreakerConfig)
            }
            state.lastAccessed = Clock.System.now().toEpochMilliseconds()
            state.failureCount++

            if (state.failureCount >= circuitBreakerConfig.failureThreshold) {
                state.isOpen = true
                state.openSince = Clock.System.now().toEpochMilliseconds()
                messagingSystem.incrementCounter("device.circuit_breaker.open",
                    tags = mapOf("device" to deviceName.toString()))
                logger.warn { "Circuit breaker for device $deviceName opened after ${state.failureCount} failures" }
            }
        }
    }

    /**
     * Records a successful restart and resets circuit breaker state
     *
     * @param deviceName Name of the device
     */
    public suspend fun recordRestartSuccess(deviceName: Name) {
        lock.withLock {
            circuitBreakerStates[deviceName]?.let { state ->
                state.failureCount = 0
                state.isOpen = false
                state.lastAccessed = Clock.System.now().toEpochMilliseconds()
                messagingSystem.incrementCounter("device.circuit_breaker.reset",
                    tags = mapOf("device" to deviceName.toString()))
                logger.info { "Circuit breaker for device $deviceName reset after successful restart" }
            }
        }
    }

    /**
     * Gets current circuit breaker status for a device
     */
    public suspend fun getCircuitBreakerStatus(deviceName: Name): Map<String, Any>? {
        return lock.withLock {
            circuitBreakerStates[deviceName]?.let { state ->
                mapOf(
                    "isOpen" to state.isOpen,
                    "failureCount" to state.failureCount,
                    "openSince" to state.openSince,
                    "thresholdFailures" to state.config.failureThreshold,
                    "resetTimeoutMs" to state.config.resetTimeout.inWholeMilliseconds,
                    "additionalTimeAfterFailureMs" to state.config.additionalTimeAfterFailure.inWholeMilliseconds
                )
            }
        }
    }

    /**
     * Resets circuit breaker state for a device
     */
    public suspend fun resetCircuitBreaker(deviceName: Name) {
        lock.withLock {
            circuitBreakerStates.remove(deviceName)
        }
    }

    /**
     * Cleans up stale circuit breaker states to prevent memory leaks
     */
    public suspend fun cleanup(maxIdleTime: Duration) {
        val now = Clock.System.now().toEpochMilliseconds()
        val maxAgeMs = maxIdleTime.inWholeMilliseconds

        lock.withLock {
            circuitBreakerStates.entries.removeAll { (_, state) ->
                now - state.lastAccessed > maxAgeMs
            }
        }
    }
}

/**
 * Manages device restart operations
 */
public class DeviceRestartManager(
    private val context: Context,
    private val registry: DeviceRegistry,
    private val lifecycleManager: DeviceLifecycleManager,
    private val circuitBreakerManager: CircuitBreakerManager,
    private val messagingSystem: MessagingSystem,
    private val logger: Logger = context.logger
) {
    private val restartAttemptsMap = mutableMapOf<Name, Int>()
    private val restartingDevices = mutableSetOf<Name>()
    private val lock = Mutex()

    /**
     * Restarts a device with proper error handling and circuit breaker pattern
     */
    public suspend fun restartDevice(name: Name): Boolean {
        val deviceJob = registry.getDeviceJob(name) ?:
        throw DeviceNotFoundInRegistryException("Device $name not found")

        if (lock.withLock { name in restartingDevices }) {
            logger.warn { "Device $name is already being restarted" }
            messagingSystem.incrementCounter("device.restart.rejected",
                tags = mapOf("device" to name.toString(), "reason" to "already_restarting"))
            return false
        }

        if (!circuitBreakerManager.shouldAttemptRestart(name, deviceJob.config.restartPolicy)) {
            logger.warn { "Circuit breaker open for $name, not attempting restart" }
            messagingSystem.incrementCounter("device.restart.rejected",
                tags = mapOf("device" to name.toString(), "reason" to "circuit_breaker_open"))
            return false
        }

        val startTime = Clock.System.now()
        messagingSystem.incrementCounter("device.restart.attempt",
            tags = mapOf("device" to name.toString()))

        try {
            lock.withLock {
                restartingDevices.add(name)

                val currentAttempts = restartAttemptsMap[name] ?: 0
                val attempts = currentAttempts + 1
                restartAttemptsMap[name] = attempts

                if (attempts > deviceJob.config.restartPolicy.maxAttempts) {
                    logger.warn { "Max restart attempts (${deviceJob.config.restartPolicy.maxAttempts}) exceeded for $name" }
                    messagingSystem.incrementCounter("device.restart.max_attempts_exceeded",
                        tags = mapOf("device" to name.toString()))
                    return@withLock false
                }

                val delayDuration = lifecycleManager.calculateRestartDelay(deviceJob.config.restartPolicy, attempts)
                if (delayDuration > Duration.ZERO) {
                    logger.info { "Delaying restart of $name by $delayDuration (attempt $attempts of ${deviceJob.config.restartPolicy.maxAttempts})" }
                    messagingSystem.recordDuration("device.restart.delay", delayDuration,
                        tags = mapOf("device" to name.toString(), "attempt" to attempts.toString()))
                    delay(delayDuration)
                }
                true
            }

            logger.info { "Restarting device $name" }

            if (deviceJob.device.lifecycleState == LifecycleState.STARTED) {
                try {
                    val stopStartTime = Clock.System.now()
                    lifecycleManager.stopDevice(name, deviceJob)
                    val stopDuration = Clock.System.now() - stopStartTime
                    messagingSystem.recordDuration("device.restart.stop_duration", stopDuration,
                        tags = mapOf("device" to name.toString()))
                } catch (e: Exception) {
                    logger.error(e) { "Error stopping device $name during restart" }
                    messagingSystem.incrementCounter("device.restart.stop_failure",
                        tags = mapOf("device" to name.toString(), "error" to e.message.toString()))
                }
            }

            registry.removeDevice(name)
            val newDeviceJob = registry.registerDevice(
                name = name,
                device = deviceJob.device,
                config = deviceJob.config,
                meta = deviceJob.meta
            ) { message ->
                messagingSystem.publish(message)
            }

            if (newDeviceJob.lifecycleMode != LifecycleMode.INDEPENDENT) {
                val startStartTime = Clock.System.now()
                lifecycleManager.startDevice(name, newDeviceJob.config, newDeviceJob.device)
                val startDuration = Clock.System.now() - startStartTime
                messagingSystem.recordDuration("device.restart.start_duration", startDuration,
                    tags = mapOf("device" to name.toString()))
            }

            if (deviceJob.config.restartPolicy.resetOnSuccess) {
                lock.withLock {
                    restartAttemptsMap.remove(name)
                }
                circuitBreakerManager.recordRestartSuccess(name)
            }

            val totalDuration = Clock.System.now() - startTime
            messagingSystem.recordDuration("device.restart.total_duration", totalDuration,
                tags = mapOf("device" to name.toString()))
            messagingSystem.incrementCounter("device.restart.success",
                tags = mapOf("device" to name.toString()))

            messagingSystem.logDevice(
                message = "Device successfully restarted",
                sourceDevice = name
            )

            return true

        } catch (e: Exception) {
            logger.error(e) { "Failed to restart device $name" }
            messagingSystem.incrementCounter("device.restart.failure",
                tags = mapOf("device" to name.toString(), "error" to e.message.toString()))

            messagingSystem.logDevice(
                message = "Failed to restart device: ${e.message}",
                sourceDevice = name
            )

            circuitBreakerManager.recordRestartFailure(name, deviceJob.config.restartPolicy)
            throw DeviceStartupException("Failed to restart device $name: ${e.message}", e)
        } finally {
            lock.withLock {
                restartingDevices.remove(name)
            }
        }
    }

    /**
     * Resets restart attempt counter for a device
     */
    public suspend fun resetRestartAttempts(deviceName: Name) {
        lock.withLock {
            restartAttemptsMap.remove(deviceName)
            restartingDevices.remove(deviceName)
        }
        circuitBreakerManager.resetCircuitBreaker(deviceName)
    }

    /**
     * Gets current restart attempt count for a device
     */
    public suspend fun getRestartAttemptCount(deviceName: Name): Int {
        return lock.withLock {
            restartAttemptsMap[deviceName] ?: 0
        }
    }

    /**
     * Cleans up stale restart tracking data
     */
    public suspend fun cleanup(maxIdleTime: Duration) {
        lock.withLock {
            restartAttemptsMap.entries.removeAll { (key, _) ->
                key !in restartingDevices
            }
        }
    }
}

/**
 * The main hub manager for device coordination.
 * Delegates specific responsibilities to component managers.
 */
public class DeviceHubManager(
    public override val context: Context,
    messageBus: MessageBus = MessageBusFactory.create(context)
) : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    private val resourceInfo: SystemResourceInfo by lazy {
        SystemResourceInfo(context)
    }

    public companion object : PluginFactory<DeviceHubManager> {
        override val tag: PluginTag = PluginTag("controls.device.hub", PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): DeviceHubManager {
            val sourceEndpoint = meta["sourceEndpoint"].string ?: "device.hub"
            val messageBus = MessageBusFactory.create(context, sourceEndpoint)

            return DeviceHubManager(context, messageBus)
        }
    }

    /**
     * Messaging system for communications
     */
    public val messagingSystem: MessagingSystem = MessagingSystem(messageBus, context.logger)

    /**
     * All messages as a flow
     */
    public val messages: Flow<Message> = messageBus.subscribe(MessageFilter.ALL)

    /**
     * Device log messages flow
     */
    public val deviceLogs: Flow<DeviceLogMessage> = messagingSystem.getDeviceLogMessages()

    /**
     * System log messages flow
     */
    public val systemLogs: Flow<SystemLogMessage> = messagingSystem.getSystemLogMessages()

    /**
     * Device state changes flow
     */
    public val deviceStateEvents: Flow<DeviceStateMessage> = messagingSystem.getDeviceStateMessages()

    /**
     * Transaction events flow
     */
    public val transactionEvents: Flow<TransactionMessage> = messagingSystem.getTransactionMessages()

    /**
     * Metrics flow
     */
    public val metricEvents: Flow<MetricMessage> = messagingSystem.getMetricMessages()

    /**
     * Metrics publisher for monitoring
     */
    public val metricsPublisher: MetricsPublisher = MetricsPublisherImpl(
        messagingSystem,
        "metrics".asName(),
        context.logger
    )

    /**
     * Transaction manager for transactional operations
     */
    public val transactionManager: TransactionManager = TransactionManagerImpl(messagingSystem, context.logger)

    /**
     * Registry for managing devices
     */
    private val deviceRegistry = DeviceRegistry(context)

    /**
     * Manager for device lifecycle operations (start, stop)
     */
    private val lifecycleManager = DeviceLifecycleManager(
        context,
        deviceRegistry,
        messagingSystem
    )

    /**
     * Manager for circuit breaker pattern
     */
    private val circuitBreakerManager = CircuitBreakerManager(
        context,
        messagingSystem
    )

    /**
     * Manager for device restart operations
     */
    private val restartManager = DeviceRestartManager(
        context,
        deviceRegistry,
        lifecycleManager,
        circuitBreakerManager,
        messagingSystem
    )

    /**
     * Global exception handler for all coroutines in this manager
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, ex ->
        context.logger.error(ex) { "Unhandled exception in DeviceHubManager scope" }
    }

    /**
     * SupervisorJob ensures that child coroutines are isolated
     */
    private val parentJob = SupervisorJob()

    /**
     * Flag indicating if this manager is active
     */
    private val isActive = atomic(true)

    /**
     * Default dispatcher for controlled concurrency
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val defaultDispatcher = Dispatchers.Default.limitedParallelism(
        resourceInfo.getConcurrencyLevel()
    )

    /**
     * Global scope for launching coroutines
     */
    private val managerScope = CoroutineScope(
        parentJob + defaultDispatcher + exceptionHandler + CoroutineName("DeviceHubManager")
    )

    /**
     * Cleanup job for resource management
     */
    private val cleanupJob: Job = managerScope.launch {
        val config = context.deviceManagerConfig
        while (isActive) {
            delay(config.resourceCleanupInterval)
            try {
                circuitBreakerManager.cleanup(config.resourceMaxIdleTime)
                restartManager.cleanup(config.resourceMaxIdleTime)
            } catch (e: Exception) {
                context.logger.error(e) { "Error during resource cleanup" }
            }
        }
    }

    /**
     * Map of all registered devices
     */
    public val devices: Map<Name, Device>
        get() = deviceRegistry.devices

    /**
     * Launches a coroutine in the manager's scope
     */
    public fun launchGlobal(block: suspend CoroutineScope.() -> Unit): Job =
        managerScope.launch { block() }

    /**
     * Records a duration metric
     */
    public suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String> = emptyMap()) {
        metricsPublisher.recordDuration(name, duration, tags)
    }

    /**
     * Increments a counter metric
     */
    public suspend fun incrementCounter(name: String, amount: Double = 1.0, tags: Map<String, String> = emptyMap()) {
        metricsPublisher.incrementCounter(name, amount, tags)
    }

    /**
     * Records a gauge metric
     */
    public suspend fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap()) {
        metricsPublisher.recordGauge(name, value, tags)
    }

    /**
     * Records a distribution metric
     */
    public suspend fun recordDistribution(name: String, value: Double, tags: Map<String, String> = emptyMap()) {
        metricsPublisher.recordDistribution(name, value, tags)
    }

    /**
     * Publishes a message to the messaging system
     */
    public suspend fun publishMessage(message: DeviceMessage) {
        messagingSystem.publish(message)
    }

    /**
     * Attaches a device to the manager
     */
    public suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        startMode: StartMode = StartMode.NONE
    ) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot attach device")
        }

        lifecycleManager.attachDevice(name, device, config, meta, startMode)
    }

    /**
     * Detaches a device from the manager
     */
    public suspend fun detachDevice(name: Name, waitStop: Boolean = false) {
        lifecycleManager.detachDevice(name, waitStop)
    }

    /**
     * Restarts a device
     */
    public suspend fun restartDevice(name: Name): Boolean {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot restart device")
        }

        return restartManager.restartDevice(name)
    }

    /**
     * Starts multiple devices in a transactional manner.
     * If any device fails to start, already started devices are rolled back (stopped).
     *
     * @param deviceNames The list of device names to start.
     * @return true if all devices started successfully, false otherwise.
     */
    public suspend fun startDevicesBatch(
        deviceNames: List<Name>
    ): Boolean = transactionManager.withTransaction { txContext ->
        val startedNames = mutableListOf<Name>()

        try {
            for (name in deviceNames) {
                val deviceJob = deviceRegistry.getDeviceJob(name)
                    ?: error("Device $name not found in registry")

                if (deviceJob.device.lifecycleState == LifecycleState.STARTED) {
                    continue
                }

                lifecycleManager.startDevice(name, deviceJob.config, deviceJob.device)
                startedNames += name
                txContext.recordAction(object : ReversibleAction {
                    override val id: String = "start_device_$name"

                    override suspend fun reverse() {
                        lifecycleManager.stopDevice(name, deviceJob)
                    }
                })
            }

            messagingSystem.logSystem(
                message = "Batch started devices: $startedNames",
                component = "DeviceHubManager"
            )
            true
        } catch (ex: Exception) {
            messagingSystem.logSystem(
                message = "Failed to start devices batch: ${ex.message}",
                component = "DeviceHubManager"
            )
            throw ex
        }
    }

    /**
     * Stops multiple devices in a transactional manner.
     */
    public suspend fun stopDevicesBatch(
        deviceNames: List<Name>
    ): Boolean = transactionManager.withTransaction { txContext ->
        val stoppedNames = mutableListOf<Name>()

        try {
            for (name in deviceNames) {
                val deviceJob = deviceRegistry.getDeviceJob(name)
                    ?: error("Device $name not found in registry")

                if (deviceJob.device.lifecycleState == LifecycleState.STARTED) {
                    lifecycleManager.stopDevice(name, deviceJob)
                    stoppedNames += name

                    txContext.recordAction(object : ReversibleAction {
                        override val id: String = "stop_device_$name"

                        override suspend fun reverse() {
                            lifecycleManager.startDevice(name, deviceJob.config, deviceJob.device)
                        }
                    })
                }
            }

            messagingSystem.logSystem(
                message = "Batch stopped devices: $stoppedNames",
                component = "DeviceHubManager"
            )
            true
        } catch (ex: Exception) {
            messagingSystem.logSystem(
                message = "Failed to stop devices batch: ${ex.message}",
                component = "DeviceHubManager"
            )
            throw ex
        }
    }

    /**
     * Hot swaps a device with a new instance
     */
    public suspend fun hotSwapDevice(
        name: Name,
        newDevice: Device,
        newConfig: DeviceLifecycleConfig,
        newMeta: Meta? = null
    ): Unit = transactionManager.withTransaction { txContext ->
        val oldJob = deviceRegistry.removeDevice(name)

        oldJob?.let { old ->
            txContext.recordAction(object : ReversibleAction {
                override val id: String = "hotSwap_$name"

                override suspend fun reverse() {
                    deviceRegistry.registerDevice(
                        name,
                        old.device,
                        old.config,
                        old.meta
                    ) { message ->
                        messagingSystem.publish(message)
                    }
                    if (old.device.lifecycleState == LifecycleState.STARTED) {
                        lifecycleManager.startDevice(name, old.config, old.device)
                    }
                }
            })
        }

        oldJob?.let {
            if (it.device.lifecycleState == LifecycleState.STARTED) {
                lifecycleManager.stopDevice(name, it)
            }
        }

        val newJob = deviceRegistry.registerDevice(
            name,
            newDevice,
            newConfig,
            newMeta
        ) { message ->
            messagingSystem.publish(message)
        }

        if (newConfig.lifecycleMode != LifecycleMode.INDEPENDENT) {
            lifecycleManager.startDevice(name, newConfig, newDevice)
        }

        messagingSystem.logSystem(
            message = "Hot-swapped device $name",
            component = "DeviceHubManager"
        )
    }

    /**
     * Checks the health of a device
     */
    public suspend fun checkHealth(name: Name): HealthReport {
        val deviceJob = deviceRegistry.getDeviceJob(name) ?:
        throw DeviceConfigurationException("Device $name not found")

        val healthChecker = deviceJob.config.healthChecker
            ?: return HealthReport(true, emptyMap(), mapOf("message" to "No health checker configured"))

        return if (healthChecker is HealthCheckerImpl) {
            healthChecker.getHealthReport(deviceJob.device)
        } else {
            val isHealthy = healthChecker.isHealthy(deviceJob.device)
            HealthReport(isHealthy, emptyMap(), mapOf("message" to "Basic health check"))
        }
    }

    /**
     * Publishes a log message to the messaging system
     */
    public suspend fun publishLog(
        deviceName: Name? = null,
        message: String
    ) {
        if (deviceName != null) {
            messagingSystem.logDevice(message, deviceName)
        } else {
            messagingSystem.logSystem(message, "DeviceHubManager")
        }
    }

    /**
     * Shuts down the device hub manager
     */
    public suspend fun shutdown() {
        if (!isActive.compareAndSet(true, false)) {
            return
        }

        context.logger.info { "Starting DeviceHubManager shutdown" }

        try {
            cleanupJob.cancelAndJoin()

            val deviceNames = deviceRegistry.getDeviceNames()
            val shutdownJobs = deviceNames.map { name ->
                launchGlobal {
                    try {
                        withTimeout(context.deviceManagerConfig.defaultStopTimeout) {
                            detachDevice(name, true)
                        }
                    } catch (e: TimeoutCancellationException) {
                        context.logger.error { "Timed out detaching device $name during shutdown" }
                    } catch (e: Exception) {
                        context.logger.error(e) { "Error detaching device $name during shutdown" }
                    }
                }
            }

            try {
                withTimeout(context.deviceManagerConfig.defaultStopTimeout.times(2)) {
                    shutdownJobs.joinAll()
                }
            } catch (_: TimeoutCancellationException) {
                context.logger.warn { "Timed out waiting for all devices to detach during shutdown" }
            }

            parentJob.cancelAndJoin()

            context.logger.info { "DeviceHubManager shutdown completed" }
        } catch (e: Exception) {
            context.logger.error(e) { "Error during shutdown" }
            parentJob.cancel()
        }
    }

    /**
     * Checks if a device exists
     */
    public suspend fun deviceExists(name: Name): Boolean {
        return deviceRegistry.containsDevice(name)
    }

    /**
     * Gets all device names
     */
    public suspend fun getAllDeviceNames(): Set<Name> {
        return deviceRegistry.getDeviceNames()
    }

    /**
     * Runs health checks on all devices in the registry
     */
    public suspend fun runHealthChecks(): Map<Name, Boolean> {
        val devices = deviceRegistry.getDevicesSafe()
        val results = mutableMapOf<Name, Boolean>()

        for ((name, _) in devices) {
            try {
                val report = checkHealth(name)
                results[name] = report.isHealthy

                if (!report.isHealthy) {
                    val deviceJob = deviceRegistry.getDeviceJob(name) ?: continue

                    if (deviceJob.config.onError == ChildDeviceErrorHandler.RESTART) {
                        launchGlobal {
                            restartDevice(name)
                        }
                    }
                }
            } catch (e: Exception) {
                context.logger.error(e) { "Error during health check for device $name" }
                results[name] = false
            }
        }

        return results
    }

    /**
     * Gets circuit breaker status for a device
     */
    public suspend fun getCircuitBreakerStatus(deviceName: Name): Map<String, Any>? {
        return circuitBreakerManager.getCircuitBreakerStatus(deviceName)
    }

    /**
     * Resets restart attempts for a device
     */
    public suspend fun resetRestartAttempts(deviceName: Name) {
        restartManager.resetRestartAttempts(deviceName)
    }

    /**
     * Gets current restart attempt count for a device
     */
    public suspend fun getRestartAttemptCount(deviceName: Name): Int {
        return restartManager.getRestartAttemptCount(deviceName)
    }
}

//endregion

//region Component Registry and Specifications

/**
 * Interface for a registry of component specifications.
 */
public interface ComponentRegistry : ContextAware {
    /**
     * Retrieves a [CompositeControlComponentSpec] by its [name].
     *
     * @param name The specification's [Name].
     * @return The [CompositeControlComponentSpec] or null if not found or type mismatch occurs.
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>?

    /**
     * Registers a component specification
     *
     * @param name The name to register the spec under
     * @param spec The specification to register
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> registerSpec(
        name: Name,
        spec: CompositeControlComponentSpec<D>
    )

    /**
     * Checks if a specification exists with the given name
     *
     * @param name The name to check
     * @return true if the specification exists
     */
    public fun hasSpec(name: Name): Boolean

    /**
     * Lists all registered specification names
     *
     * @return Set of registered specification names
     */
    public fun listSpecs(): Set<Name>
}

/**
 * Default implementation of ComponentRegistry that stores specifications in memory
 */
public class DefaultComponentRegistry(
    override val context: Context
) : ComponentRegistry {
    private val registry = mutableMapOf<Name, CompositeControlComponentSpec<*>>()

    override fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>? {
        @Suppress("UNCHECKED_CAST")
        return registry[name] as? CompositeControlComponentSpec<D>
    }

    override fun <D : ConfigurableCompositeControlComponent<D>> registerSpec(
        name: Name,
        spec: CompositeControlComponentSpec<D>
    ) {
        registry[name] = spec
    }

    override fun hasSpec(name: Name): Boolean = name in registry

    override fun listSpecs(): Set<Name> = registry.keys.toSet()
}

/**
 * Interface representing configuration for a child component.
 *
 * @param CD The type of the child device.
 */
public interface ChildComponentConfig<CD : ConfigurableCompositeControlComponent<CD>> {
    /** The specification of the child component. */
    public val spec: CompositeControlComponentSpec<CD>
    /** The lifecycle configuration for the child component. */
    public val config: DeviceLifecycleConfig
    /** Optional metadata for the child component. */
    public val meta: Meta?
    /** The name of the child component. */
    public val name: Name

    public companion object {
        /**
         * Creates a ChildComponentConfig from Meta
         */
        public fun <CD : ConfigurableCompositeControlComponent<CD>> fromMeta(
            meta: Meta,
            registry: ComponentRegistry,
            name: Name
        ): ChildComponentConfig<CD>? {
            val specName = meta["spec"].string?.asName() ?: return null
            val spec = registry.getSpec<CD>(specName) ?: return null

            val configBuilder = DeviceLifecycleConfigBuilder()
            meta["config"]?.let { configMeta ->
                configBuilder.lifecycleMode = configMeta["lifecycleMode"]?.string?.let {
                    LifecycleMode.valueOf(it)
                } ?: LifecycleMode.LINKED

                configBuilder.messageBuffer = configMeta["messageBuffer"]?.int ?: 1000
                configBuilder.startDelay = configMeta["startDelay"]?.string?.let { Duration.parse(it) } ?: Duration.ZERO
                configBuilder.startTimeout = configMeta["startTimeout"]?.string?.let { Duration.parse(it) }
                configBuilder.stopTimeout = configMeta["stopTimeout"]?.string?.let { Duration.parse(it) }
                configBuilder.onError = configMeta["onError"]?.string?.let {
                    ChildDeviceErrorHandler.valueOf(it)
                } ?: ChildDeviceErrorHandler.RESTART
            }

            val deviceMeta = meta["meta"]

            return object : ChildComponentConfig<CD> {
                override val spec: CompositeControlComponentSpec<CD> = spec
                override val config: DeviceLifecycleConfig = configBuilder.build()
                override val meta: Meta? = deviceMeta
                override val name: Name = name
            }
        }

        /**
         * Creates a ChildComponentConfig with fluent builder
         */
        public fun <CD : ConfigurableCompositeControlComponent<CD>> builder(
            spec: CompositeControlComponentSpec<CD>,
            name: Name
        ): Builder<CD> = Builder(spec, name)

        /**
         * Builder for creating ChildComponentConfig instances
         */
        public class Builder<CD : ConfigurableCompositeControlComponent<CD>>(
            private val spec: CompositeControlComponentSpec<CD>,
            private val name: Name
        ) {
            private val configBuilder = DeviceLifecycleConfigBuilder()
            private var meta: Meta? = null

            public fun withLifecycleConfig(config: DeviceLifecycleConfig): Builder<CD> {
                configBuilder.lifecycleMode = config.lifecycleMode
                configBuilder.messageBuffer = config.messageBuffer
                configBuilder.startDelay = config.startDelay
                configBuilder.startTimeout = config.startTimeout
                configBuilder.stopTimeout = config.stopTimeout
                configBuilder.coroutineScope = config.coroutineScope
                configBuilder.dispatcher = config.dispatcher
                configBuilder.onError = config.onError
                configBuilder.healthChecker = config.healthChecker
                configBuilder.restartPolicy = config.restartPolicy
                return this
            }

            public fun withConfigBuilder(block: DeviceLifecycleConfigBuilder.() -> Unit): Builder<CD> {
                configBuilder.apply(block)
                return this
            }

            public fun withMeta(meta: Meta): Builder<CD> {
                this.meta = meta
                return this
            }

            public fun withMeta(block: MutableMeta.() -> Unit): Builder<CD> {
                this.meta = Meta(block)
                return this
            }

            public fun build(): ChildComponentConfig<CD> = object : ChildComponentConfig<CD> {
                override val spec: CompositeControlComponentSpec<CD> = this@Builder.spec
                override val config: DeviceLifecycleConfig = configBuilder.build()
                override val meta: Meta? = this@Builder.meta
                override val name: Name = this@Builder.name
            }
        }
    }
}

/**
 * Interface defining a composite device specification with properties, actions, and child specs.
 *
 * @param D The device type this spec applies to.
 */
public interface CompositeDeviceSpec<D : ConfigurableCompositeControlComponent<D>> {
    /** Map of property specifications. */
    public val properties: Map<String, DevicePropertySpec<D, *>>
    /** Map of action specifications. */
    public val actions: Map<String, DeviceActionSpec<D, *, *>>
    /** Map of child component configurations. */
    public val childSpecs: Map<String, ChildComponentConfig<*>>

    /**
     * Called when the device is opening (starting).
     *
     * @receiver The device instance.
     */
    public suspend fun D.onOpen()

    /**
     * Called when the device is closing (stopping).
     *
     * @receiver The device instance.
     */
    public suspend fun D.onClose()

    /**
     * Validates the [device]'s state or properties. Throws an exception if validation fails.
     *
     * @param device The device instance to validate.
     */
    public fun validate(device: D)

    /**
     * Registers a [deviceProperty].
     *
     * @param deviceProperty The property specification to register.
     * @return The registered property specification.
     */
    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P

    /**
     * Registers a [deviceAction].
     *
     * @param deviceAction The action specification to register.
     * @return The registered action specification.
     */
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    /**
     * Declares a read-only property with the given [converter].
     *
     * @param converter The meta converter for the property.
     * @param descriptorBuilder Optional builder for the property descriptor.
     * @param name An optional name override.
     * @param read A suspend function to read the property value.
     * @return A property delegate provider for the declared property.
     */
    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>>

    /**
     * Declares a mutable property with the given [converter].
     *
     * @param converter The meta converter for the property.
     * @param descriptorBuilder Optional builder for the property descriptor.
     * @param name An optional name override.
     * @param read A suspend function to read the property value.
     * @param write A suspend function to write the property value.
     * @return A property delegate provider for the declared mutable property.
     */
    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>>

    /**
     * Declares an action with the specified input and output converters and execution block.
     *
     * @param inputConverter The meta converter for the action input.
     * @param outputConverter The meta converter for the action output.
     * @param descriptorBuilder Optional builder for the action descriptor.
     * @param name An optional name override.
     * @param execute The suspend function to execute the action.
     * @return A property delegate provider for the declared action.
     */
    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>>
}

/**
 * Default implementation of [CompositeDeviceSpec].
 *
 * @param D The device type.
 * @param registry Optional [ComponentRegistry] for looking up child specifications.
 */
@OptIn(InternalDeviceAPI::class)
public open class CompositeControlComponentSpec<D : ConfigurableCompositeControlComponent<D>>(
    public val registry: ComponentRegistry? = null
) : CompositeDeviceSpec<D> {
    private val propertyMap = hashMapOf<String, DevicePropertySpec<D, *>>()
    private val actionMap = hashMapOf<String, DeviceActionSpec<D, *, *>>()
    private val childSpecMap = mutableMapOf<String, ChildComponentConfig<*>>()

    // Associated DeviceState definitions
    private val stateMap = mutableMapOf<String, DeviceState<*>>()

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = propertyMap

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = actionMap

    override val childSpecs: Map<String, ChildComponentConfig<*>>
        get() = childSpecMap

    /**
     * Map of state objects defined in this spec
     */
    public val states: Map<String, DeviceState<*>>
        get() = stateMap

    override suspend fun D.onOpen() {
        // Default implementation is no-op.
    }

    override suspend fun D.onClose() {
        // Default implementation is no-op.
    }

    override fun validate(device: D) {
        this.validateSpec(device)
    }

    override fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P {
        if (propertyMap[deviceProperty.name] != null) {
            throw DeviceConfigurationException("Property ${deviceProperty.name} is already registered.")
        }
        propertyMap[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    override fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
        if (actionMap[deviceAction.name] != null) {
            throw DeviceConfigurationException("Action ${deviceAction.name} is already registered.")
        }
        actionMap[deviceAction.name] = deviceAction
        return deviceAction
    }

    /**
     * Registers a DeviceState associated with this component
     */
    public fun <T, S : DeviceState<T>> registerState(name: String, state: S): S {
        if (stateMap.containsKey(name)) {
            throw DeviceConfigurationException("State with name $name is already registered")
        }
        stateMap[name] = state
        return state
    }

    /**
     * Creates a property descriptor
     */
    private fun createPropertyDescriptor(
        propertyName: String,
        converter: MetaConverter<*>,
        mutable: Boolean,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit
    ): PropertyDescriptor {
        return propertyDescriptor(propertyName) {
            this.mutable = mutable
            converter.descriptor?.let { conv -> metaDescriptor { from(conv) } }
            descriptorBuilder()
        }
    }

    /**
     * Creates an action descriptor
     */
    private fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit
    ): ActionDescriptor {
        return actionDescriptor(actionName) {
            inputConverter.descriptor?.let { convIn -> inputMeta { from(convIn) } }
            outputConverter.descriptor?.let { convOut -> outputMeta { from(convOut) } }
            descriptorBuilder()
        }
    }

    override fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptor(
                propertyName, converter, mutable = false, descriptorBuilder
            )
            val devProp = registerProperty(object : DevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = descriptor
                override val converter: MetaConverter<T> = converter
                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }
            })
            ReadOnlyProperty { _, _ -> devProp }
        }

    override fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptor(
                propertyName, converter, mutable = true, descriptorBuilder
            )
            val devProp = registerProperty(object : MutableDevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = descriptor
                override val converter: MetaConverter<T> = converter
                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }

                override suspend fun write(device: D, value: T) =
                    withContext(device.coroutineContext) { device.write(propertyName, value) }
            })
            ReadOnlyProperty { _, _ -> devProp }
        }

    /**
     * Declares a child specification, using [fallbackSpec] or retrieving from [registry].
     *
     * @param fallbackSpec The default spec if not found in registry.
     * @param specKeyInRegistry Optional registry key.
     * @param childDeviceName Optional explicit name for the child device.
     * @param metaBuilder Optional lambda to build child [Meta].
     * @param configBuilder Lambda to configure the child's [DeviceLifecycleConfig].
     */
    public fun <CDS : CompositeControlComponentSpec<CD>, CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        fallbackSpec: CDS,
        specKeyInRegistry: Name? = null,
        childDeviceName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {}
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            val registryKey = specKeyInRegistry ?: property.name.asName()
            val cName = childDeviceName ?: property.name.asName()
            val config = DeviceLifecycleConfigBuilder().apply(configBuilder).build()
            val meta = metaBuilder?.let { Meta(it) }
            val fromRegistry: CompositeControlComponentSpec<CD>? = thisRef.registry?.getSpec<CD>(registryKey)
            val foundSpec: CompositeControlComponentSpec<CD> = fromRegistry ?: fallbackSpec
            val mapKey = cName.toString()

            if (thisRef.childSpecMap[mapKey] != null) {
                throw DeviceConfigurationException("Child spec with name '$mapKey' is already registered in $thisRef.")
            }
            val childConfig = object : ChildComponentConfig<CD> {
                override val spec: CompositeControlComponentSpec<CD> = foundSpec
                override val config: DeviceLifecycleConfig = config
                override val meta: Meta? = meta
                override val name: Name = cName
            }
            thisRef.childSpecMap[mapKey] = childConfig
            ReadOnlyProperty { _, _ -> foundSpec }
        }

    public fun <CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        spec: CompositeControlComponentSpec<CD>,
        nameBuilder: () -> Name = { "unnamed".asName() },
        configBuilder: ChildComponentConfig.Companion.Builder<CD>.() -> Unit
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            val name = nameBuilder()
            val builder = ChildComponentConfig.builder(spec, name).apply(configBuilder)
            val config = builder.build()
            val mapKey = name.toString()

            if (thisRef.childSpecMap[mapKey] != null) {
                throw DeviceConfigurationException("Child spec with name '$mapKey' is already registered in $thisRef.")
            }

            thisRef.childSpecMap[mapKey] = config
            ReadOnlyProperty { _, _ -> spec }
        }

    override fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit,
        name: String?,
        execute: suspend D.(I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _, property ->
            val actionName = name ?: property.name
            val descriptor = createActionDescriptor(
                actionName, inputConverter, outputConverter, descriptorBuilder
            )
            val devAction = registerAction(object : DeviceActionSpec<D, I, O> {
                override val descriptor: ActionDescriptor = descriptor
                override val inputConverter: MetaConverter<I> = inputConverter
                override val outputConverter: MetaConverter<O> = outputConverter
                override suspend fun execute(device: D, input: I): O =
                    try {
                        withContext(device.coroutineContext) { device.execute(input) }
                    } catch (ex: Exception) {
                        device.logger.error(ex) { "Error executing action $actionName on device ${device.id}" }
                        throw ex
                    }
            })
            ReadOnlyProperty { _, _ -> devAction }
        }

    /**
     * Registers a DeviceState that will be linked to a property
     */
    public fun <T> stateProperty(
        state: DeviceState<T>,
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptor(
                propertyName, converter, mutable = state is MutableDeviceState, descriptorBuilder
            )

            // Register state under the property name
            registerState(propertyName, state)

            val devProp = if (state is MutableDeviceState) {
                registerProperty(object : MutableDevicePropertySpec<D, T> {
                    override val descriptor: PropertyDescriptor = descriptor
                    override val converter: MetaConverter<T> = converter

                    override suspend fun read(device: D): T? = state.value

                    override suspend fun write(device: D, value: T) {
                        state.value = value
                    }
                })
            } else {
                registerProperty(object : DevicePropertySpec<D, T> {
                    override val descriptor: PropertyDescriptor = descriptor
                    override val converter: MetaConverter<T> = converter

                    override suspend fun read(device: D): T? = state.value
                })
            }

            ReadOnlyProperty { _, _ -> devProp }
        }
}

/**
 * Declares an action with [Unit] input and output.
 *
 * @param descriptorBuilder Optional metadata configuration.
 * @param name Optional override for the action name.
 * @param execute The action logic.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.unitAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.() -> Unit
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    action(MetaConverter.unit, MetaConverter.unit, descriptorBuilder, name) { execute() }

/**
 * Declares an action with [Meta] input and output.
 *
 * @param descriptorBuilder Optional metadata configuration.
 * @param name Optional override for the action name.
 * @param execute The action logic.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(Meta) -> Meta
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    action(MetaConverter.meta, MetaConverter.meta, descriptorBuilder, name) { execute(it) }

//endregion

//region Composite Device Implementation

/**
 * Interface for a composite control component that hosts child devices.
 */
public interface CompositeControlComponent : Device {
    /**
     * A map of child devices keyed by their [Name].
     */
    public val devices: Map<Name, Device>
}

/**
 * A configurable composite device supporting child components via a [spec].
 *
 * @param D Self-referential type for the device.
 * @param spec The [CompositeControlComponentSpec] defining properties, actions, and children.
 * @param context The parent [Context].
 * @param meta Device metadata.
 * @param config The [DeviceLifecycleConfig] for this device.
 * @param deviceHubManager The [DeviceHubManager] instance.
 */
public open class ConfigurableCompositeControlComponent<D : ConfigurableCompositeControlComponent<D>>(
    public val spec: CompositeControlComponentSpec<D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
    config: DeviceLifecycleConfig = DeviceLifecycleConfig(),
    public val deviceHubManager: DeviceHubManager = DeviceHubManager(context)
) : DeviceBase<D>(context, meta), CompositeControlComponent {

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = spec.properties

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = spec.actions

    override fun toString(): String = "Device(id=$id, spec=$spec)"

    override val devices: Map<Name, Device>
        get() = deviceHubManager.devices

    /**
     * Container for device states defined in the spec
     */
    protected val stateContainer: StateContainerImpl = StateContainerImpl(this)

    protected val childConfigs: List<ChildComponentConfig<*>> = spec.childSpecs.values.toList()

    private val childInitializationStatus = mutableMapOf<Name, Boolean>()
    private val initLock = Mutex()

    /**
     * Implementation of state container to manage and interact with device states
     */
    protected class StateContainerImpl(device: Device) : StateContainer {
        override val context: Context = device.context
        override val coroutineContext: CoroutineContext = device.coroutineContext

        private val elements = mutableSetOf<ConstructorElement>()

        override val constructorElements: Set<ConstructorElement>
            get() = elements

        override fun registerElement(constructorElement: ConstructorElement) {
            elements.add(constructorElement)
        }

        override fun unregisterElement(constructorElement: ConstructorElement) {
            elements.remove(constructorElement)
        }
    }

    init {
        for ((name, state) in spec.states) {
            stateContainer.registerState(state)
        }

        // Register action handlers
        deviceHubManager.launchGlobal {
            spec.actions.values.forEach { actionSpec ->
                messageFlow
                    .filterIsInstance<ActionExecuteMessage>()
                    .filter { it.action == actionSpec.name }
                    .onEach { msg ->
                        try {
                            val result = execute(actionSpec.name, msg.argument)
                            val resultMessage = ActionResultMessage(
                                action = actionSpec.name,
                                result = result,
                                requestId = msg.requestId,
                                sourceDevice = id.asName()
                            )
                            // Response is automatically handled by the message bus
                            context.logger.debug { "Action ${actionSpec.name} executed successfully on device $id" }
                            deviceHubManager.publishMessage(resultMessage)
                        } catch (ex: Exception) {
                            logger.error(ex) { "Error executing action ${actionSpec.name} on device $id" }
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    /**
     * Initializes child devices defined in the spec
     */
    public suspend fun initChildren() {
        for (childCfg in childConfigs) {
            if (initLock.withLock { childInitializationStatus[childCfg.name] == true }) {
                continue
            }

            val config = DeviceLifecycleConfigBuilder().apply {
                lifecycleMode = childCfg.config.lifecycleMode
                messageBuffer = childCfg.config.messageBuffer
                startDelay = childCfg.config.startDelay
                startTimeout = childCfg.config.startTimeout
                stopTimeout = childCfg.config.stopTimeout
                coroutineScope = childCfg.config.coroutineScope
                dispatcher = childCfg.config.dispatcher
                onError = childCfg.config.onError
                healthChecker = childCfg.config.healthChecker
                restartPolicy = childCfg.config.restartPolicy
            }.build()

            try {
                val childSpec = childCfg.spec
                val childDevice: ConfigurableCompositeControlComponent<*> = if (childSpec is DeviceSpecification<*>) {
                    childSpec.deviceFactory(context, childCfg.meta ?: Meta.EMPTY)
                } else {
                    ConfigurableCompositeControlComponent(
                        childSpec,
                        context,
                        childCfg.meta ?: Meta.EMPTY,
                        config,
                        deviceHubManager
                    )
                }

                deviceHubManager.attachDevice(childCfg.name, childDevice, config, childCfg.meta, StartMode.NONE)
                initLock.withLock {
                    childInitializationStatus[childCfg.name] = true
                }
            } catch (e: Exception) {
                logger.error(e) { "Error initializing child device ${childCfg.name}" }
                initLock.withLock {
                    childInitializationStatus[childCfg.name] = false
                }
                if (config.onError == ChildDeviceErrorHandler.PROPAGATE) {
                    throw e
                }
            }
        }
    }

    /**
     * Called when this device is starting.
     * The default logic calls [spec.onOpen], validates the device,
     * and then automatically starts child devices if they are [LifecycleMode.LINKED].
     */
    override suspend fun onStart() {
        with(spec) {
            self.onOpen()
            validate(self)
        }
        initChildren()
        val childDevices = deviceHubManager.getAllDeviceNames()
            .filter { name -> initLock.withLock { childInitializationStatus[name] == true } }

        for (name in childDevices) {
            try {
                deviceHubManager.devices[name]?.start()
            } catch (e: Exception) {
                logger.error(e) { "Error starting child device $name during parent start" }
                val childConfig = childConfigs.find { it.name == name }?.config
                if (childConfig?.onError == ChildDeviceErrorHandler.PROPAGATE) {
                    throw e
                }
                logger.warn { "Continuing despite error in child device $name" }
            }
        }
    }

    /**
     * Called when the device stops.
     */
    override suspend fun onStop() {
        val runningChildren = deviceHubManager.devices.entries
            .filter { (_, device) -> device.lifecycleState == LifecycleState.STARTED }

        val stopJobs = runningChildren.map { (name, device) ->
            launch(device.coroutineContext) {
                try {
                    device.stop()
                } catch (e: Exception) {
                    logger.error(e) { "Error stopping child device $name during parent stop" }
                }
            }
        }
        stopJobs.joinAll()

        with(spec) {
            self.onClose()
        }
    }

    /**
     * Retrieves a child device by its [name].
     *
     * @throws IllegalStateException if the child device is not found or if there is a type mismatch.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <CD : ConfigurableCompositeControlComponent<CD>> getChildDevice(name: Name): CD {
        return deviceHubManager.devices[name] as? CD ?: error("Child device $name not found or type mismatch.")
    }

    /**
     * Provides a property delegate to retrieve a child device by [name] or by the property name if [name] is null.
     */
    public fun <CD : ConfigurableCompositeControlComponent<CD>> childDevice(name: Name? = null):
            PropertyDelegateProvider<ConfigurableCompositeControlComponent<D>, ReadOnlyProperty<ConfigurableCompositeControlComponent<D>, CD>> =
        PropertyDelegateProvider { _, property ->
            ReadOnlyProperty { _, _ ->
                val devName = name ?: property.name.asName()
                getChildDevice<CD>(devName)
            }
        }

    /**
     * Operator to retrieve a child device by [Name].
     */
    public inline operator fun <reified Dev : Device> get(name: Name): Dev? = devices[name] as? Dev

    /**
     * Operator to retrieve a child device by a string name.
     */
    public inline operator fun <reified Dev : Device> get(name: String): Dev? = this[name.asName()]

    /**
     * Gets a state by name from the spec
     */
    public fun <T> getState(name: String): DeviceState<T>? {
        @Suppress("UNCHECKED_CAST")
        return spec.states[name] as? DeviceState<T>
    }

    /**
     * Gets a mutable state by name
     */
    public fun <T> getMutableState(name: String): MutableDeviceState<T>? {
        @Suppress("UNCHECKED_CAST")
        return spec.states[name] as? MutableDeviceState<T>
    }

    /**
     * Delegate provider to access a state by property name
     */
    public fun <T> state(name: String? = null): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, DeviceState<T>>> =
        PropertyDelegateProvider { _, property ->
            val stateName = name ?: property.name
            val state = getState<T>(stateName)
                ?: throw IllegalStateException("State with name $stateName not found in ${id}")
            ReadOnlyProperty { _, _ -> state }
        }

    /**
     * Delegate provider to access a mutable state
     */
    public fun <T> mutableState(name: String? = null): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, MutableDeviceState<T>>> =
        PropertyDelegateProvider { _, property ->
            val stateName = name ?: property.name
            val state = getMutableState<T>(stateName)
                ?: throw IllegalStateException("Mutable state with name $stateName not found in ${id}")
            ReadOnlyProperty { _, _ -> state }
        }
}

/**
 * Stops a device with a timeout, logging a warning if it fails to stop in time.
 *
 * @param timeout The maximum time to wait for stopping.
 */
public suspend fun WithLifeCycle.stopWithTimeout(timeout: Duration = 10.seconds) {
    try {
        withTimeout(timeout) {
            stop()
        }
    } catch (_: TimeoutCancellationException) {
        (this as? DeviceBase<*>)?.logger?.warn { "Timeout on stop for device ${this.id}" }
    } catch (e: Exception) {
        (this as? DeviceBase<*>)?.logger?.error(e) { "Error stopping device ${this.id}" }
        throw e
    }
}

/**
 * Abstract base class for specifying a [ConfigurableCompositeControlComponent].
 *
 * @param D The device type.
 * @param deviceFactory Factory function to create the device.
 */
public abstract class DeviceSpecification<D : ConfigurableCompositeControlComponent<D>>(
    public val deviceFactory: (Context, Meta) -> D
) : CompositeControlComponentSpec<D>()

/**
 * Extension to get DeviceHubManager from context
 */
public val Context.deviceHubManager: DeviceHubManager
    get() = plugins[DeviceHubManager] ?: throw IllegalStateException(
        "DeviceHubManager not found in context. Add it with context.plugin(DeviceHubManager)."
    )

//endregion