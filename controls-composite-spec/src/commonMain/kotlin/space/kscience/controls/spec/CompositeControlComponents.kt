@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package space.kscience.controls.spec

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.api.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.spec.DeviceErrorCategory.CRITICAL
import space.kscience.controls.spec.DeviceErrorCategory.NON_CRITICAL
import space.kscience.controls.spec.LifecycleMode.*
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.send
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Categoriзation of errors by severity:
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
    public open val category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : RuntimeException(message, cause)

/**
 * Exception thrown when a connection to a device fails
 */
public class DeviceConnectionException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Exception thrown when a device operation times out
 */
public class DeviceTimeoutException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Exception thrown when a device configuration is invalid
 */
public class DeviceConfigurationException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Exception thrown when a device operation fails due to concurrent access
 */
public class DeviceConcurrencyException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Exception thrown when a device operation fails during startup
 */
public class DeviceStartupException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Exception thrown when a device operation fails during shutdown
 */
public class DeviceShutdownException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Exception thrown when a device state transition is invalid
 */
public class DeviceStateTransitionException(
    message: String,
    cause: Throwable? = null,
    category: DeviceErrorCategory = DeviceErrorCategory.CRITICAL
) : DeviceException(message, cause, category)

/**
 * Magix message format definitions for device management
 */
public object DeviceControlsFormats {

    /**
     * Format for device messages
     */
    public val DEVICE_MESSAGE_FORMAT: MagixFormat<DeviceMessage> = MagixFormat(
        DeviceMessage.serializer(),
        setOf("controls.device.message")
    )

    /**
     * Format for system log messages
     */
    public val SYSTEM_LOG_FORMAT: MagixFormat<SystemLogPayload> = MagixFormat(
        SystemLogPayload.serializer(),
        setOf("controls.system.log")
    )

    /**
     * Format for device state events
     */
    public val DEVICE_STATE_FORMAT: MagixFormat<DeviceStatePayload> = MagixFormat(
        DeviceStatePayload.serializer(),
        setOf("controls.device.state")
    )

    /**
     * Format for metrics
     */
    public val METRICS_FORMAT: MagixFormat<MetricPayload> = MagixFormat(
        MetricPayload.serializer(),
        setOf("controls.metrics")
    )

    /**
     * Format for transaction events
     */
    public val TRANSACTION_FORMAT: MagixFormat<TransactionPayload> = MagixFormat(
        TransactionPayload.serializer(),
        setOf("controls.transaction")
    )
}

/**
 * Payload for system log messages
 */
@Serializable
public sealed class SystemLogPayload {
    public abstract val message: String
    public abstract val deviceName: String?
    public abstract val timestamp: Long
    public abstract val severity: String

    @Serializable
    @SerialName("system.log.message")
    public data class LogMessage(
        override val message: String,
        override val deviceName: String? = null,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        override val severity: String = "INFO",
        val details: Map<String, String> = emptyMap()
    ) : SystemLogPayload()

    @Serializable
    @SerialName("system.log.error")
    public data class ErrorMessage(
        override val message: String,
        val errorType: String? = null,
        val stackTrace: String? = null,
        override val deviceName: String? = null,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        override val severity: String = "ERROR"
    ) : SystemLogPayload()
}

/**
 * Payload for device state events
 */
@Serializable
public sealed class DeviceStatePayload {
    public abstract val deviceName: String

    @Serializable
    @SerialName("device.state.added")
    public data class DeviceAdded(
        override val deviceName: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : DeviceStatePayload()

    @Serializable
    @SerialName("device.state.started")
    public data class DeviceStarted(
        override val deviceName: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : DeviceStatePayload()

    @Serializable
    @SerialName("device.state.stopped")
    public data class DeviceStopped(
        override val deviceName: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : DeviceStatePayload()

    @Serializable
    @SerialName("device.state.removed")
    public data class DeviceRemoved(
        override val deviceName: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : DeviceStatePayload()

    @Serializable
    @SerialName("device.state.failed")
    public data class DeviceFailed(
        override val deviceName: String,
        val errorMessage: String,
        val errorType: String? = null,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : DeviceStatePayload()

    @Serializable
    @SerialName("device.state.detached")
    public data class DeviceDetached(
        override val deviceName: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : DeviceStatePayload()
}

/**
 * Payload for metric events
 */
@Serializable
public sealed class MetricPayload {
    public abstract val name: String
    public abstract val timestamp: Long

    @Serializable
    @SerialName("metrics.value")
    public data class MetricValue(
        override val name: String,
        val value: Double,
        val tags: Map<String, String> = emptyMap(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : MetricPayload()

    @Serializable
    @SerialName("metrics.counter")
    public data class MetricCounter(
        override val name: String,
        val increment: Double = 1.0,
        val tags: Map<String, String> = emptyMap(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : MetricPayload()

    @Serializable
    @SerialName("metrics.duration")
    public data class MetricDuration(
        override val name: String,
        val durationMs: Long,
        val tags: Map<String, String> = emptyMap(),
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : MetricPayload()
}

/**
 * Payload for transaction events
 */
@Serializable
public sealed class TransactionPayload {
    public abstract val transactionId: String

    @Serializable
    @SerialName("transaction.started")
    public data class TransactionStarted(
        override val transactionId: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : TransactionPayload()

    @Serializable
    @SerialName("transaction.committed")
    public data class TransactionCommitted(
        override val transactionId: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : TransactionPayload()

    @Serializable
    @SerialName("transaction.rolled_back")
    public data class TransactionRolledBack(
        override val transactionId: String,
        val errorMessage: String? = null,
        val errorType: String? = null,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : TransactionPayload()
}

/**
 * DeviceManager configuration plugin for the context
 */
public class DeviceManagerConfig(
    public val messageBufferSize: Int = 1000,
    public val defaultConcurrencyLevel: Int = 4,
    public val defaultStartTimeout: Duration = 30.seconds,
    public val defaultStopTimeout: Duration = 10.seconds
) : AbstractPlugin() {
    override val tag: PluginTag get() = Companion.tag

    init {
        require(messageBufferSize > 0) { "Message buffer size must be positive" }
        require(defaultConcurrencyLevel > 0) { "Concurrency level must be positive" }
    }

    public companion object : PluginFactory<DeviceManagerConfig> {
        override val tag: PluginTag = PluginTag("controls.device.config", PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): DeviceManagerConfig {
            val messageBuffer = meta["messageBufferSize"].int ?: 1000
            val concurrencyLevel = meta["defaultConcurrencyLevel"].int ?: 4
            val startTimeout = meta["defaultStartTimeout"]?.string?.let { Duration.parse(it) } ?: 30.seconds
            val stopTimeout = meta["defaultStopTimeout"]?.string?.let { Duration.parse(it) } ?: 10.seconds

            return DeviceManagerConfig(
                messageBufferSize = messageBuffer,
                defaultConcurrencyLevel = concurrencyLevel,
                defaultStartTimeout = startTimeout,
                defaultStopTimeout = stopTimeout
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
     * Gets the list of actions that have been recorded in this transaction.
     *
     * @return A list of [ReversibleAction] instances.
     */
    public suspend fun getActions(): List<ReversibleAction> = mutex.withLock {
        actions.toList()
    }
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
}

/**
 * Element for storing transaction context in coroutine context
 */
private class TransactionContextElement(val context: TransactionContext) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TransactionContextElement>

    override val key: CoroutineContext.Key<TransactionContextElement> = Key
}

/**
 * Implementation of [TransactionManager] with rollback support based on Magix.
 *
 * @param magixEndpoint The [MagixEndpoint] to publish transaction events.
 * @param sourceEndpoint The name of this endpoint for Magix messages.
 * @param logger The logger for transaction operations.
 */
public class MagixTransactionManager(
    private val magixEndpoint: MagixEndpoint,
    private val sourceEndpoint: String,
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
            magixEndpoint.send(
                DeviceControlsFormats.TRANSACTION_FORMAT,
                TransactionPayload.TransactionStarted(txId),
                sourceEndpoint
            )

            val result = withContext(contextWithTransaction) {
                block(txContext)
            }

            magixEndpoint.send(
                DeviceControlsFormats.TRANSACTION_FORMAT,
                TransactionPayload.TransactionCommitted(txId),
                sourceEndpoint
            )

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

            magixEndpoint.send(
                DeviceControlsFormats.TRANSACTION_FORMAT,
                TransactionPayload.TransactionRolledBack(
                    txId,
                    ex.message,
                    ex::class.simpleName
                ),
                sourceEndpoint
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

    private fun generateTransactionId(): String = "tx_${Clock.System.now().toEpochMilliseconds()}"
}

/**
 * Enum defining how a child device's lifecycle is coupled to its parent.
 *
 * - [LINKED] Child starts/stops with the parent.
 * - [INDEPENDENT] Child must be manually started/stopped, independent of parent lifecycle.
 * - [LAZY] Child is created but only starts on explicit request.
 */
public enum class LifecycleMode {
    LINKED,
    INDEPENDENT,
    LAZY
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
     * @return `true` if healthy, `false` otherwise.
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
    PROPAGATE,

    /** Use a custom strategy defined in [DeviceHubManager.onCustomError]. */
    CUSTOM
}

/**
 * Data class describing restart behavior when [ChildDeviceErrorHandler.RESTART] is used.
 *
 * @property maxAttempts Maximum number of restart attempts.
 * @property delayBetweenAttempts Base delay between restart attempts.
 * @property resetOnSuccess Whether to reset the attempt counter on successful start.
 * @property strategy The [RestartStrategy] for calculating delay.
 * @property circuitBreaker Optional circuit breaker configuration.
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
    }
}

/**
 * Circuit breaker configuration for error handling.
 */
public data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val resetTimeout: Duration = 60.seconds
)

/**
 * Enum defining how delays are calculated for restart attempts.
 */
public enum class RestartStrategy {
    /** Fixed delay using [RestartPolicy.delayBetweenAttempts]. */
    LINEAR,

    /** Exponential backoff, e.g., delay * 2^(attempt-1). */
    EXPONENTIAL_BACKOFF,

    /** Placeholder for custom strategies (requires override). */
    CUSTOM
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
 * Functional interface to apply external configurations to a [DeviceLifecycleConfigBuilder].
 */
public fun interface ExternalConfigApplier {
    /**
     * Applies configuration to the [builder] for the device named [deviceName].
     *
     * @param builder The [DeviceLifecycleConfigBuilder] to configure.
     * @param deviceName The device's [Name].
     */
    public suspend fun applyConfig(builder: DeviceLifecycleConfigBuilder, deviceName: Name)
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

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.LINKED].
 */
public fun DeviceLifecycleConfigBuilder.linked() {
    lifecycleMode = LifecycleMode.LINKED
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.INDEPENDENT].
 */
public fun DeviceLifecycleConfigBuilder.independent() {
    lifecycleMode = LifecycleMode.INDEPENDENT
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.LAZY].
 */
public fun DeviceLifecycleConfigBuilder.lazy() {
    lifecycleMode = LifecycleMode.LAZY
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.onError] to [ChildDeviceErrorHandler.RESTART].
 */
public fun DeviceLifecycleConfigBuilder.restartOnError() {
    onError = ChildDeviceErrorHandler.RESTART
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.onError] to [ChildDeviceErrorHandler.PROPAGATE].
 */
public fun DeviceLifecycleConfigBuilder.propagateError() {
    onError = ChildDeviceErrorHandler.PROPAGATE
}

/**
 * Sets both [DeviceLifecycleConfigBuilder.startTimeout] and [DeviceLifecycleConfigBuilder.stopTimeout]
 * to the provided [timeout].
 *
 * @param timeout The timeout value to be used.
 */
public fun DeviceLifecycleConfigBuilder.withCustomTimeout(timeout: Duration) {
    startTimeout = timeout
    stopTimeout = timeout
}

/**
 * Interface for a registry of component specifications.
 */
public interface ComponentRegistry : ContextAware {
    /**
     * Retrieves a [CompositeControlComponentSpec] by its [name].
     *
     * @param name The specification's [Name].
     * @return The [CompositeControlComponentSpec] or `null` if not found or type mismatch occurs.
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>?
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
     * Creates a [PropertyDescriptor] internally.
     *
     * @param propertyName The name of the property.
     * @param converter The meta converter for the property.
     * @param mutable Indicates if the property is mutable.
     * @param property The [KProperty] reference.
     * @param descriptorBuilder A builder function to further configure the descriptor.
     * @return The created [PropertyDescriptor].
     */
    public fun createPropertyDescriptorInternal(
        propertyName: String,
        converter: MetaConverter<*>,
        mutable: Boolean,
        property: KProperty<*>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit
    ): PropertyDescriptor

    /**
     * Creates an [ActionDescriptor].
     *
     * @param actionName The name of the action.
     * @param inputConverter The meta converter for the input.
     * @param outputConverter The meta converter for the output.
     * @param property The [KProperty] reference.
     * @param descriptorBuilder A builder function to further configure the descriptor.
     * @return The created [ActionDescriptor].
     */
    public fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        property: KProperty<*>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit
    ): ActionDescriptor

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
    private val specLock = Mutex()
    private val propertyMap = hashMapOf<String, DevicePropertySpec<D, *>>()
    private val actionMap = hashMapOf<String, DeviceActionSpec<D, *, *>>()
    private val childSpecMap = mutableMapOf<String, ChildComponentConfig<*>>()

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = propertyMap

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = actionMap

    override val childSpecs: Map<String, ChildComponentConfig<*>>
        get() = childSpecMap

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

    override fun createPropertyDescriptorInternal(
        propertyName: String,
        converter: MetaConverter<*>,
        mutable: Boolean,
        property: KProperty<*>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit
    ): PropertyDescriptor {
        return propertyDescriptor(propertyName) {
            this.mutable = mutable
            converter.descriptor?.let { conv -> metaDescriptor { from(conv) } }
            descriptorBuilder()
        }
    }

    override fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        property: KProperty<*>,
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
            val descriptor = createPropertyDescriptorInternal(
                propertyName, converter, mutable = false, property = property, descriptorBuilder = descriptorBuilder
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
            val descriptor = createPropertyDescriptorInternal(
                propertyName, converter, mutable = true, property = property, descriptorBuilder = descriptorBuilder
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

    override fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit,
        name: String?,
        execute: suspend D.(I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _, property ->
            val actionName = name ?: property.name
            val descriptor = createActionDescriptor(actionName, inputConverter, outputConverter, property, descriptorBuilder)
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

/**
 * Registry for managing device lifecycle and operations.
 * Maintains a registry of devices and handles their lifecycle events.
 */
public class DeviceRegistry(
    private val context: Context
) {
    private val childLock = Mutex()
    private val childrenJobs = mutableMapOf<Name, DeviceHubManager.ChildJob>()

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
     * @return The [DeviceHubManager.ChildJob] that was created.
     */
    public suspend fun registerDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null
    ): DeviceHubManager.ChildJob = childLock.withLock {
        val scope = config.coroutineScope ?: CoroutineScope(
            config.dispatcher ?: (Dispatchers.Default +
                    SupervisorJob() +
                    CoroutineName("Device-$name"))
        )

        val collectorJob = scope.launch(CoroutineName("Collect device $name")) {
            try {
                device.messageFlow.collect { msg ->
                    val wrapped = msg.changeSource { name.plus(it) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                throw ex
            }
        }

        val childJob = DeviceHubManager.ChildJob(
            device = device,
            collectorJob = collectorJob,
            config = config,
            meta = meta
        )

        childrenJobs[name] = childJob
        return childJob
    }

    /**
     * Gets a child device by name.
     *
     * @param name The name of the device.
     * @return The [DeviceHubManager.ChildJob] or null if not found.
     */
    public suspend fun getChildJob(name: Name): DeviceHubManager.ChildJob? = childLock.withLock {
        return childrenJobs[name]
    }

    /**
     * Removes a device from the registry.
     *
     * @param name The name of the device.
     * @return The removed [DeviceHubManager.ChildJob] or null if not found.
     */
    public suspend fun removeDevice(name: Name): DeviceHubManager.ChildJob? = childLock.withLock {
        return childrenJobs.remove(name)
    }

    /**
     * Updates a device in the registry.
     *
     * @param name The name of the device.
     * @param job The new [DeviceHubManager.ChildJob].
     */
    public suspend fun updateDevice(name: Name, job: DeviceHubManager.ChildJob): Unit = childLock.withLock {
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
 * Abstract base class for Device Hub managers.
 * Provides core functionality for managing devices, handling errors,
 * and coordinating device lifecycle operations.
 */
public abstract class DeviceHubManager(
    public override val context: Context,
    public val magixEndpoint: MagixEndpoint,
    public val sourceEndpoint: String
): AbstractPlugin() {
    /**
     * Represents a running child device along with its job, configuration, and metadata.
     */
    public data class ChildJob(
        val device: Device,
        val collectorJob: Job,
        val config: DeviceLifecycleConfig,
        val meta: Meta? = null
    ) {
        val lifecycleMode: LifecycleMode get() = config.lifecycleMode
    }

    override val tag: PluginTag get() = Companion.tag

    public companion object : PluginFactory<DeviceHubManager>{
        override val tag: PluginTag
            get() = TODO("Not yet implemented")

        override fun build(
            context: Context,
            meta: Meta,
        ): DeviceHubManager {
            TODO("Not yet implemented")
        }

    }

    /**
     * Returns a map of all registered devices.
     */
    public abstract val devices: Map<Name, Device>

    /**
     * A [TransactionManager] for transactional operations.
     */
    public abstract val transactionManager: TransactionManager

    /**
     * Global function for launching coroutines.
     *
     * @param block The suspend function to execute.
     * @return The launched [Job].
     */
    public abstract fun launchGlobal(block: suspend CoroutineScope.() -> Unit): Job

    /**
     * Called when an error is thrown from a child's coroutine.
     *
     * @param ex The thrown exception.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration of the child device.
     */
    public abstract suspend fun onChildErrorCaught(ex: Throwable, childName: Name, config: DeviceLifecycleConfig)

    /**
     * Called if a child error triggers [ChildDeviceErrorHandler.STOP_PARENT].
     *
     * @param ex The exception that caused the stop.
     * @param childName The name of the child device.
     */
    public abstract suspend fun onParentStopRequested(ex: Throwable, childName: Name)

    /**
     * Called if [ChildDeviceErrorHandler.CUSTOM] is used.
     *
     * @param ex The exception that occurred.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration for the child device.
     */
    public abstract suspend fun onCustomError(ex: Throwable, childName: Name, config: DeviceLifecycleConfig)

    /**
     * Called when a device times out while starting.
     *
     * @param deviceName The name of the device.
     * @param config The lifecycle configuration for the device.
     */
    public abstract suspend fun onStartTimeout(deviceName: Name, config: DeviceLifecycleConfig)

    /**
     * Called when a device times out while stopping.
     *
     * @param deviceName The name of the device.
     * @param config The lifecycle configuration for the device.
     */
    public abstract suspend fun onStopTimeout(deviceName: Name, config: DeviceLifecycleConfig)

    /**
     * Performs a health check on a device.
     *
     * @param childJob The [ChildJob] representing the device.
     */
    public abstract suspend fun checkHealth(childJob: ChildJob)

    /**
     * Attaches a device to the manager.
     *
     * @param name The unique name of the device.
     * @param device The [Device] instance to attach.
     * @param config The lifecycle configuration for the device.
     * @param meta Optional metadata for the device.
     * @param startMode Determines whether to auto-start the device.
     */
    public abstract suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        startMode: StartMode = StartMode.NONE
    )

    /**
     * Detaches a device from the manager.
     *
     * @param name The unique name of the device.
     * @param waitStop If true, waits for the device to stop.
     */
    public abstract suspend fun detachDevice(name: Name, waitStop: Boolean = false)

    /**
     * Restarts a device.
     *
     * @param name The unique name of the device to restart.
     */
    public abstract suspend fun restartDevice(name: Name)

    /**
     * Changes the lifecycle mode for a device.
     *
     * @param name The unique name of the device.
     * @param newMode The new lifecycle mode.
     */
    public abstract suspend fun changeLifecycleMode(name: Name, newMode: LifecycleMode)

    /**
     * Replaces a device with a new one.
     *
     * @param name The unique name of the device to replace.
     * @param newDevice The new [Device] instance.
     * @param config The new lifecycle configuration.
     * @param meta Optional metadata.
     */
    public abstract suspend fun hotSwapDevice(
        name: Name,
        newDevice: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null
    )

    /**
     * Renames a device.
     *
     * @param oldName The current name of the device.
     * @param newName The new name for the device.
     */
    public abstract suspend fun renameDevice(oldName: Name, newName: Name)

    /**
     * Starts multiple devices transactionally.
     *
     * @param deviceNames The list of device names to start.
     * @return `true` if all devices started successfully, `false` otherwise.
     */
    public abstract suspend fun startDevicesBatch(deviceNames: List<Name>): Boolean

    /**
     * Stops multiple devices transactionally.
     *
     * @param deviceNames The list of device names to stop.
     * @return `true` if all devices were stopped successfully, `false` otherwise.
     */
    public abstract suspend fun stopDevicesBatch(deviceNames: List<Name>): Boolean

    /**
     * Runs health checks on all devices.
     */
    public abstract suspend fun runHealthChecks(): Map<Name, Boolean>

    /**
     * Shuts down the manager.
     */
    public abstract suspend fun shutdown()

    /**
     * Checks if a device exists.
     *
     * @param name The device name.
     * @return True if the device exists, false otherwise.
     */
    public abstract suspend fun deviceExists(name: Name): Boolean

    /**
     * Gets all device names.
     *
     * @return A set of all device names.
     */
    public abstract suspend fun getAllDeviceNames(): Set<Name>

    /**
     * Helper for starting a device.
     *
     * @param name The device name.
     * @param config The lifecycle configuration.
     * @param device The device instance.
     */
    protected abstract suspend fun doStartDevice(name: Name, config: DeviceLifecycleConfig, device: Device)

    /**
     * Publishes a system log message through Magix.
     */
    public suspend fun publishSystemLog(
        message: String,
        deviceName: String? = null,
        severity: String = "INFO",
        details: Map<String, String> = emptyMap()
    ) {
        magixEndpoint.send(
            DeviceControlsFormats.SYSTEM_LOG_FORMAT,
            SystemLogPayload.LogMessage(
                message = message,
                deviceName = deviceName,
                severity = severity,
                details = details
            ),
            sourceEndpoint
        )
    }

    /**
     * Publishes a device state change event through Magix.
     */
    public suspend fun publishDeviceState(payload: DeviceStatePayload) {
        magixEndpoint.send(
            DeviceControlsFormats.DEVICE_STATE_FORMAT,
            payload,
            sourceEndpoint
        )
    }

    /**
     * Publishes a metric through Magix.
     */
    public suspend fun publishMetric(name: String, value: Double, tags: Map<String, String> = emptyMap()) {
        magixEndpoint.send(
            DeviceControlsFormats.METRICS_FORMAT,
            MetricPayload.MetricValue(name, value, tags),
            sourceEndpoint
        )
    }

    /**
     * Increments a counter metric through Magix.
     */
    public suspend fun incrementCounter(name: String, tags: Map<String, String> = emptyMap()) {
        magixEndpoint.send(
            DeviceControlsFormats.METRICS_FORMAT,
            MetricPayload.MetricCounter(name, 1.0, tags),
            sourceEndpoint
        )
    }

    /**
     * Records a duration metric through Magix.
     */
    public suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String> = emptyMap()) {
        magixEndpoint.send(
            DeviceControlsFormats.METRICS_FORMAT,
            MetricPayload.MetricDuration(name, duration.inWholeMilliseconds, tags),
            sourceEndpoint
        )
    }
}

/**
 * Standard implementation of [DeviceHubManager] with Magix.
 */
public class MagixDeviceHubManager(
    context: Context,
    magixEndpoint: MagixEndpoint,
    sourceEndpoint: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    resourceInfo: SystemResourceInfo = SystemResourceInfo(context)
) : DeviceHubManager(context, magixEndpoint, sourceEndpoint) {

    override val tag: PluginTag get() = TODO()

    public companion object : PluginFactory<MagixDeviceHubManager> {
        override val tag: PluginTag = PluginTag("controls.device.TODO()", PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): MagixDeviceHubManager {

            return TODO()
        }
    }

    /**
     * Global exception handler for all coroutines in this manager.
     */
    private val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, ex ->
        context.logger.error(ex) { "Unhandled exception in global scope (DeviceHubManager)" }
    }

    /**
     * SupervisorJob ensures that child coroutines are isolated.
     */
    private val parentJob: Job = SupervisorJob()

    /**
     * Flag indicating if this manager is active
     */
    private val isActive = atomic(true)

    /**
     * Dispatcher for controlled concurrency
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val limitedDispatcher = dispatcher.limitedParallelism(resourceInfo.getConcurrencyLevel())

    /**
     * Registry for managing devices
     */
    private val deviceRegistry = DeviceRegistry(context)

    /**
     * Transaction manager for wrapping critical operations.
     */
    override val transactionManager: TransactionManager = MagixTransactionManager(magixEndpoint, sourceEndpoint, context.logger)

    /**
     * Returns a map of all registered devices.
     */
    override val devices: Map<Name, Device>
        get() = deviceRegistry.devices

    /**
     * Global function for launching coroutines with the combined context.
     *
     * @param block The suspend function to execute.
     * @return The launched [Job].
     */
    override fun launchGlobal(block: suspend CoroutineScope.() -> Unit): Job =
        CoroutineScope(parentJob + limitedDispatcher + exceptionHandler + CoroutineName("DeviceHub")).launch { block() }

    /**
     * Called when an error is thrown from a child's coroutine.
     *
     * @param ex The thrown exception.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration of the child device.
     */
    override suspend fun onChildErrorCaught(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        val category = if (ex is DeviceException) ex.category else DeviceErrorCategory.CRITICAL

        publishMetric("device.error", 1.0, mapOf("device" to childName.toString(), "category" to category.toString()))

        when (category) {
            DeviceErrorCategory.CRITICAL -> {
                context.logger.error(ex) {
                    "CRITICAL error in child device $childName with policy ${config.onError}"
                }

                val errorMessage = DeviceMessage.error(ex, childName)

                publishDeviceState(DeviceStatePayload.DeviceFailed(
                    childName.toString(),
                    ex.message ?: "Unknown error",
                    ex::class.simpleName
                ))

                when (config.onError) {
                    ChildDeviceErrorHandler.IGNORE -> {
                        publishMetric("device.error.ignored", 1.0, mapOf("device" to childName.toString()))
                    }
                    ChildDeviceErrorHandler.RESTART -> {
                        launchGlobal {
                            restartDevice(childName)
                        }
                    }
                    ChildDeviceErrorHandler.STOP_PARENT -> {
                        publishMetric("device.error.stop_parent", 1.0, mapOf("device" to childName.toString()))
                        onParentStopRequested(ex, childName)
                    }
                    ChildDeviceErrorHandler.PROPAGATE -> {
                        publishMetric("device.error.propagated", 1.0, mapOf("device" to childName.toString()))
                        throw ex
                    }
                    ChildDeviceErrorHandler.CUSTOM -> {
                        publishMetric("device.error.custom_handler", 1.0, mapOf("device" to childName.toString()))
                        onCustomError(ex, childName, config)
                    }
                }
            }

            DeviceErrorCategory.NON_CRITICAL -> {
                context.logger.warn {
                    "NON_CRITICAL error in child device $childName, continuing with policy ${config.onError}"
                }
                publishMetric("device.error.non_critical", 1.0, mapOf("device" to childName.toString()))
            }
        }
    }

    /**
     * Called if a child error triggers [ChildDeviceErrorHandler.STOP_PARENT], indicating the parent must stop.
     *
     * @param ex The exception that caused the stop.
     * @param childName The name of the child device.
     */
    override suspend fun onParentStopRequested(ex: Throwable, childName: Name) {
        context.logger.error(ex) { "Stopping parent due to error in child $childName" }
        withContext(NonCancellable) {
            shutdown()
        }
    }

    /**
     * Called if [ChildDeviceErrorHandler.CUSTOM] is used.
     * Override to implement a custom strategy for error handling.
     *
     * @param ex The exception that occurred.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration for the child device.
     */
    override suspend fun onCustomError(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Custom error strategy for device $childName: override onCustomError if needed." }
    }

    /**
     * Called when a device times out while starting.
     *
     * @param deviceName The name of the device.
     * @param config The lifecycle configuration for the device.
     * @throws DeviceTimeoutException When the start timeout is reached.
     */
    override suspend fun onStartTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        val msg = "Timeout while starting $deviceName."
        context.logger.error { msg }
        publishMetric("device.start.timeout", 1.0, mapOf("device" to deviceName.toString()))
        throw DeviceTimeoutException(msg)
    }

    /**
     * Called when a device times out while stopping.
     *
     * @param deviceName The name of the device.
     * @param config The lifecycle configuration for the device.
     */
    override suspend fun onStopTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.warn { "Timeout while stopping $deviceName." }
        publishMetric("device.stop.timeout", 1.0, mapOf("device" to deviceName.toString()))
    }

    /**
     * Performs a health check on the given child device.
     *
     * @param childJob The [ChildJob] representing the device.
     */
    override suspend fun checkHealth(childJob: ChildJob) {
        val childName = childJob.device.id.parseAsName()
        val healthChecker = childJob.config.healthChecker ?: return

        val startTime = Clock.System.now()
        try {
            val isHealthy = healthChecker.isHealthy(childJob.device)
            val endTime = Clock.System.now()
            val duration = endTime - startTime

            recordDuration("device.health.check.duration", duration,
                mapOf("device" to childName.toString()))

            if (isHealthy) {
                incrementCounter("device.health.check.success",
                    mapOf("device" to childName.toString()))
            } else {
                incrementCounter("device.health.check.failure",
                    mapOf("device" to childName.toString()))

                if (healthChecker is HealthCheckerImpl) {
                    val report = healthChecker.getHealthReport(childJob.device)
                    for ((key, value) in report.metrics) {
                        publishMetric("device.health.$key", value,
                            mapOf("device" to childName.toString()))
                    }
                }

                publishDeviceState(DeviceStatePayload.DeviceFailed(
                    childName.toString(),
                    "Health check failed",
                    "DeviceConnectionException"
                ))

                if (childJob.config.onError == ChildDeviceErrorHandler.RESTART) {
                    restartDevice(childName)
                }
            }
        } catch (ex: Exception) {
            context.logger.error(ex) { "Error during health check for device $childName" }
            incrementCounter("device.health.check.error",
                mapOf("device" to childName.toString()))
            publishDeviceState(DeviceStatePayload.DeviceFailed(
                childName.toString(),
                ex.message ?: "Unknown error during health check",
                ex::class.simpleName
            ))
        }
    }

    /**
     * Attaches (registers) a device in the manager under the given [name], using the provided [config] and optional [meta].
     *
     * @param name The unique name of the device.
     * @param device The [Device] instance to attach.
     * @param config The lifecycle configuration for the device.
     * @param meta Optional metadata for the device.
     * @param startMode Determines whether to auto-start the device.
     */
    override suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta?,
        startMode: StartMode
    ) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot attach device")
        }

        if (await { deviceRegistry.containsDevice(name) }) {
            throw DeviceConfigurationException("Device with name $name already exists")
        }

        if (device.lifecycleState !in listOf(LifecycleState.INITIAL, LifecycleState.STOPPED)) {
            throw DeviceConfigurationException("Device must be in INITIAL or STOPPED state to be attached, but was ${device.lifecycleState}")
        }

        val childJob = await {
            deviceRegistry.registerDevice(
                name = name,
                device = device,
                config = config,
                meta = meta
            )
        }

        publishDeviceState(DeviceStatePayload.DeviceAdded(name.toString()))
        publishSystemLog("Device $name attached, startMode=$startMode", name.toString())
        publishMetric("device.attach", 1.0, mapOf("device" to name.toString(), "startMode" to startMode.toString()))

        if (config.lifecycleMode == LifecycleMode.INDEPENDENT) return

        when (startMode) {
            StartMode.NONE -> Unit
            StartMode.ASYNC -> launchGlobal { doStartDevice(name, config, device) }
            StartMode.SYNC -> doStartDevice(name, config, device)
        }
    }

    /**
     * Helper that starts a device while respecting [startDelay] and [startTimeout].
     *
     * @param name The device name.
     * @param config The lifecycle configuration.
     * @param device The device instance.
     */
    override suspend fun doStartDevice(name: Name, config: DeviceLifecycleConfig, device: Device) {
        if (!isActive.value) {
            context.logger.warn { "DeviceHubManager is shutting down, not starting device $name" }
            return
        }

        val state = device.lifecycleState
        if (state != LifecycleState.INITIAL && state != LifecycleState.STOPPED) {
            context.logger.warn { "Cannot start device $name because it is in state $state" }
            return
        }

        if (config.startDelay > Duration.ZERO) delay(config.startDelay)

        val startTime = Clock.System.now()
        try {
            val startTimeout = config.startTimeout ?: context.deviceManagerConfig.defaultStartTimeout

            withTimeout(startTimeout) {
                if (device.lifecycleState == LifecycleState.STARTED) {
                    context.logger.warn { "Device $name is already started." }
                    return@withTimeout
                }
                device.start()
            }

            val endTime = Clock.System.now()
            val startDuration = endTime - startTime

            publishDeviceState(DeviceStatePayload.DeviceStarted(name.toString()))
            recordDuration("device.start.duration", startDuration,
                mapOf("device" to name.toString()))
            incrementCounter("device.start.success",
                mapOf("device" to name.toString()))
        } catch (e: TimeoutCancellationException) {
            incrementCounter("device.start.failure",
                mapOf("device" to name.toString(), "reason" to "timeout"))
            onStartTimeout(name, config)
        } catch (e: Exception) {
            incrementCounter("device.start.failure",
                mapOf("device" to name.toString(), "reason" to "error"))
            context.logger.error(e) { "Error starting device $name" }
            publishDeviceState(DeviceStatePayload.DeviceFailed(
                name.toString(),
                e.message ?: "Failed to start device",
                "DeviceStartupException"
            ))
            throw e
        }
    }

    /**
     * Detaches (removes) a device from the manager by its [name].
     * If [waitStop] is true, waits until the device has fully stopped.
     *
     * @param name The unique name of the device.
     * @param waitStop If true, waits for the device to stop.
     */
    override suspend fun detachDevice(name: Name, waitStop: Boolean) {
        val childJob = await { deviceRegistry.removeDevice(name) }

        if (childJob != null) {
            publishDeviceState(DeviceStatePayload.DeviceRemoved(name.toString()))
            publishSystemLog("Device $name removed (waitStop=$waitStop)", name.toString())
            incrementCounter("device.detach",
                mapOf("device" to name.toString(), "waitStop" to waitStop.toString()))

            if (waitStop) {
                performStop(childJob)
            } else {
                launchGlobal { performStop(childJob) }
            }
        }
    }

    /**
     * Restarts a device, preserving its [DeviceLifecycleConfig] and [Meta].
     * This method stops the device (if running) and relaunches it.
     *
     * @param name The unique name of the device to restart.
     */
    override suspend fun restartDevice(name: Name) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot restart device")
        }

        val childJob = await { deviceRegistry.getChildJob(name) } ?:
        throw DeviceConfigurationException("Device $name not found")

        incrementCounter("device.restart", mapOf("device" to name.toString()))

        if (childJob.device.lifecycleState == LifecycleState.STARTED) {
            try {
                performStop(childJob)
            } catch (e: Exception) {
                context.logger.error(e) { "Error stopping device $name during restart" }
                // Continue with restart even if stop failed
            }
        }

        await { deviceRegistry.removeDevice(name) }

        val newChildJob = await {
            deviceRegistry.registerDevice(
                name = name,
                device = childJob.device,
                config = childJob.config,
                meta = childJob.meta
            )
        }

        publishSystemLog("Device $name restarted", name.toString())

        if (childJob.lifecycleMode != LifecycleMode.INDEPENDENT) {
            doStartDevice(name, childJob.config, childJob.device)
        }
    }

    /**
     * Changes the [LifecycleMode] for the specified device.
     * The device is stopped and then re-attached with the new mode.
     *
     * @param name The unique name of the device.
     * @param newMode The new lifecycle mode.
     */
    override suspend fun changeLifecycleMode(name: Name, newMode: LifecycleMode) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot change lifecycle mode")
        }

        val childJob = await { deviceRegistry.getChildJob(name) } ?:
        throw DeviceConfigurationException("Device $name not found")

        val newConfig = childJob.config.copy(lifecycleMode = newMode)

        if (childJob.device.lifecycleState == LifecycleState.STARTED) {
            performStop(childJob)
        }

        await { deviceRegistry.removeDevice(name) }

        val newChildJob = await {
            deviceRegistry.registerDevice(
                name = name,
                device = childJob.device,
                config = newConfig,
                meta = childJob.meta
            )
        }

        publishSystemLog("Device $name lifecycle changed to $newMode", name.toString())
        incrementCounter("device.lifecycle.mode.change",
            mapOf("device" to name.toString(), "newMode" to newMode.toString()))

        if (newMode != LifecycleMode.INDEPENDENT) {
            doStartDevice(name, newConfig, childJob.device)
        }
    }

    /**
     * Replaces a device ("hot swap") under the same [name].
     *
     * @param name The unique name of the device to replace.
     * @param newDevice The new [Device] instance.
     * @param config The new lifecycle configuration.
     * @param meta Optional metadata.
     */
    override suspend fun hotSwapDevice(
        name: Name,
        newDevice: Device,
        config: DeviceLifecycleConfig,
        meta: Meta?
    ) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot hot swap device")
        }

        transactionManager.withTransaction { txContext ->
            val oldChildJob = await { deviceRegistry.getChildJob(name) }

            if (oldChildJob != null) {
                if (oldChildJob.device.lifecycleState == LifecycleState.STARTED) {
                    performStop(oldChildJob)
                }
                await { deviceRegistry.removeDevice(name) }

                txContext.recordAction(object : ReversibleAction {
                    override val id = "hot_swap_$name"

                    override suspend fun reverse() {
                        await {
                            deviceRegistry.registerDevice(
                                name = name,
                                device = oldChildJob.device,
                                config = oldChildJob.config,
                                meta = oldChildJob.meta
                            )
                        }
                    }
                })
            }

            val newChildJob = await {
                deviceRegistry.registerDevice(
                    name = name,
                    device = newDevice,
                    config = config,
                    meta = meta
                )
            }

            publishSystemLog("Device $name hot-swapped", name.toString())
            incrementCounter("device.hotswap", mapOf("device" to name.toString()))

            if (config.lifecycleMode != LifecycleMode.INDEPENDENT) {
                doStartDevice(name, config, newDevice)
            }
        }
    }

    /**
     * Performs the stopping sequence:
     * 1) Attempts to stop the device (with [stopTimeout] if specified).
     * 2) Cancels and joins the collector job.
     *
     * @param childJob The [ChildJob] representing the device.
     */
    private suspend fun performStop(childJob: ChildJob) {
        val timeout = childJob.config.stopTimeout ?: context.deviceManagerConfig.defaultStopTimeout
        val deviceName = childJob.device.id.parseAsName()

        // Validate state transition
        val state = childJob.device.lifecycleState
        if (state != LifecycleState.STARTED) {
            context.logger.warn { "Device $deviceName is not in STARTED state (current: $state)" }
            return
        }

        val startTime = Clock.System.now()
        incrementCounter("device.stop.attempt", mapOf("device" to deviceName.toString()))

        try {
            withTimeout(timeout) {
                childJob.device.stop()
            }

            val endTime = Clock.System.now()
            val stopDuration = endTime - startTime
            recordDuration("device.stop.duration", stopDuration, mapOf("device" to deviceName.toString()))
            incrementCounter("device.stop.success", mapOf("device" to deviceName.toString()))
        } catch (e: TimeoutCancellationException) {
            incrementCounter("device.stop.failure",
                mapOf("device" to deviceName.toString(), "reason" to "timeout"))
            onStopTimeout(deviceName, childJob.config)
        } catch (e: Exception) {
            incrementCounter("device.stop.failure",
                mapOf("device" to deviceName.toString(), "reason" to "error"))
            context.logger.error(e) { "Error stopping device $deviceName" }
            throw DeviceShutdownException("Failed to stop device $deviceName", e)
        } finally {
            withContext(NonCancellable) {
                try {
                    childJob.collectorJob.cancelAndJoin()
                } catch (e: Exception) {
                    context.logger.error(e) { "Error cancelling collector job for device $deviceName" }
                }
            }
        }

        publishDeviceState(DeviceStatePayload.DeviceStopped(deviceName.toString()))
    }

    /**
     * Renames a device dynamically. This moves the [ChildJob] from [oldName] to [newName].
     * If [newName] already exists, an exception is thrown to prevent collisions.
     *
     * @param oldName The current name of the device.
     * @param newName The new name for the device.
     * @throws DeviceConfigurationException If a device with [newName] already exists.
     */
    override suspend fun renameDevice(oldName: Name, newName: Name) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is shutting down, cannot rename device")
        }

        if (await { deviceRegistry.containsDevice(newName) }) {
            throw DeviceConfigurationException("A device with name $newName already exists; cannot rename.")
        }

        val oldChildJob = await { deviceRegistry.getChildJob(oldName) } ?:
        throw DeviceConfigurationException("Device not found: $oldName")

        await { deviceRegistry.removeDevice(oldName) }
        await {
            deviceRegistry.registerDevice(
                name = newName,
                device = oldChildJob.device,
                config = oldChildJob.config,
                meta = oldChildJob.meta
            )
        }

        publishSystemLog("Device renamed from $oldName to $newName", newName.toString())
        incrementCounter("device.rename",
            mapOf("oldName" to oldName.toString(), "newName" to newName.toString()))
    }

    /**
     * Starts multiple devices in a transactional manner.
     * If any device fails to start, already started devices are rolled back (stopped).
     *
     * @param deviceNames The list of device names to start.
     * @return `true` if all devices started successfully, `false` otherwise.
     */
    override suspend fun startDevicesBatch(deviceNames: List<Name>): Boolean = transactionManager.withTransaction { txContext ->
        val startedDevices = mutableListOf<Name>()

        incrementCounter("device.start.batch", mapOf("count" to deviceNames.size.toString()))
        val startTime = Clock.System.now()

        try {
            for (name in deviceNames) {
                val childJob = await { deviceRegistry.getChildJob(name) } ?: continue

                if (childJob.lifecycleMode != LifecycleMode.LAZY &&
                    (childJob.device.lifecycleState == LifecycleState.INITIAL ||
                            childJob.device.lifecycleState == LifecycleState.STOPPED)) {

                    doStartDevice(name, childJob.config, childJob.device)
                    startedDevices.add(name)
                    txContext.recordAction(object : ReversibleAction {
                        override val id: String = "start_device_$name"

                        override suspend fun reverse() {
                            val device = await { deviceRegistry.getChildJob(name) }?.device ?: return
                            try {
                                device.stop()
                                publishDeviceState(DeviceStatePayload.DeviceStopped(name.toString()))
                            } catch (e: Exception) {
                                context.logger.error(e) { "Error undoing start for device $name" }
                            }
                        }
                    })
                }
            }

            val endTime = Clock.System.now()
            val duration = endTime - startTime
            recordDuration("device.start.batch.duration", duration, mapOf("count" to deviceNames.size.toString()))
            incrementCounter("device.start.batch.success", mapOf("count" to deviceNames.size.toString()))

            return@withTransaction true
        } catch (ex: Exception) {
            context.logger.error(ex) { "Failed to start device batch. Rolling back will be handled by transaction manager." }
            incrementCounter("device.start.batch.failure", mapOf("count" to deviceNames.size.toString()))
            throw ex
        }
    }

    /**
     * Stops multiple devices in a transactional manner.
     * If any device fails to stop, already stopped devices are rolled back (started).
     *
     * @param deviceNames The list of device names to stop.
     * @return `true` if all devices were stopped successfully, `false` otherwise.
     */
    override suspend fun stopDevicesBatch(deviceNames: List<Name>): Boolean = transactionManager.withTransaction { txContext ->
        val stoppedDevices = mutableListOf<Name>()

        incrementCounter("device.stop.batch", mapOf("count" to deviceNames.size.toString()))
        val startTime = Clock.System.now()

        try {
            for (name in deviceNames) {
                val childJob = await { deviceRegistry.getChildJob(name) } ?: continue

                if (childJob.device.lifecycleState == LifecycleState.STARTED) {
                    val timeout = childJob.config.stopTimeout ?: context.deviceManagerConfig.defaultStopTimeout

                    try {
                        withTimeout(timeout) {
                            childJob.device.stop()
                        }

                        publishDeviceState(DeviceStatePayload.DeviceStopped(name.toString()))
                        stoppedDevices.add(name)

                        txContext.recordAction(object : ReversibleAction {
                            override val id: String = "stop_device_$name"

                            override suspend fun reverse() {
                                val device = await { deviceRegistry.getChildJob(name) }?.device ?: return
                                try {
                                    device.start()
                                    publishDeviceState(DeviceStatePayload.DeviceStarted(name.toString()))
                                } catch (e: Exception) {
                                    context.logger.error(e) { "Error undoing stop for device $name" }
                                }
                            }
                        })
                    } catch (e: TimeoutCancellationException) {
                        onStopTimeout(name, childJob.config)
                        throw e
                    }
                }
            }

            val endTime = Clock.System.now()
            val duration = endTime - startTime
            recordDuration("device.stop.batch.duration", duration, mapOf("count" to deviceNames.size.toString()))
            incrementCounter("device.stop.batch.success", mapOf("count" to deviceNames.size.toString()))

            return@withTransaction true
        } catch (ex: Exception) {
            context.logger.error(ex) { "Failed to stop device batch. Rolling back will be handled by transaction manager." }
            incrementCounter("device.stop.batch.failure", mapOf("count" to deviceNames.size.toString()))
            throw ex
        }
    }

    /**
     * Runs health checks on all registered devices.
     *
     * @return Map of device names to health check results.
     */
    override suspend fun runHealthChecks(): Map<Name, Boolean> {
        val devices = await { deviceRegistry.getDevicesSafe() }
        val results = mutableMapOf<Name, Boolean>()

        incrementCounter("device.health.check.batch")
        val startTime = Clock.System.now()

        for ((name, _) in devices) {
            val childJob = await { deviceRegistry.getChildJob(name) } ?: continue
            val healthChecker = childJob.config.healthChecker ?: continue

            try {
                val isHealthy = healthChecker.isHealthy(childJob.device)
                results[name] = isHealthy

                if (!isHealthy && childJob.config.onError == ChildDeviceErrorHandler.RESTART) {
                    launchGlobal {
                        restartDevice(name)
                    }
                }
            } catch (e: Exception) {
                context.logger.error(e) { "Error during health check for device $name" }
                results[name] = false
            }
        }

        val endTime = Clock.System.now()
        val duration = endTime - startTime
        recordDuration("device.health.check.batch.duration", duration)

        val healthyCount = results.values.count { it }
        val unhealthyCount = results.size - healthyCount

        publishMetric("device.health.check.aggregate.healthy", healthyCount.toDouble())
        publishMetric("device.health.check.aggregate.unhealthy", unhealthyCount.toDouble())
        publishMetric("device.health.check.aggregate.percentage",
            if (results.isNotEmpty()) (healthyCount.toDouble() / results.size * 100.0) else 0.0)

        return results
    }

    /**
     * Shuts down the device hub manager by cancelling the parent job.
     */
    override suspend fun shutdown() {
        if (!isActive.compareAndSet(true, false)) {
            return
        }

        context.logger.info { "Starting device hub manager shutdown" }
        incrementCounter("device.hub.shutdown")
        val startTime = Clock.System.now()

        try {
            val deviceNames = await { deviceRegistry.getDeviceNames() }
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
            } catch (e: TimeoutCancellationException) {
                context.logger.warn { "Timed out waiting for all devices to detach during shutdown" }
            }

            parentJob.cancelAndJoin()

            val endTime = Clock.System.now()
            val duration = endTime - startTime
            context.logger.info { "Device hub manager shutdown completed in $duration" }
        } catch (e: Exception) {
            context.logger.error(e) { "Error during shutdown" }
            parentJob.cancel()
        }
    }

    /**
     * Checks if a device exists.
     *
     * @param name The device name.
     * @return True if the device exists, false otherwise.
     */
    override suspend fun deviceExists(name: Name): Boolean {
        return await { deviceRegistry.containsDevice(name) }
    }

    /**
     * Gets all device names.
     *
     * @return A set of all device names.
     */
    override suspend fun getAllDeviceNames(): Set<Name> {
        return await { deviceRegistry.getDeviceNames() }
    }

    /**
     * Helper function to safely execute suspending functions with proper error handling.
     *
     * @param T The return type of the function.
     * @param block The suspending function to execute.
     * @return The result of the function.
     */
    private suspend fun <T> await(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.logger.error(e) { "Error in await block" }
            throw e
        }
    }
}

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
 * @param hubManager The [DeviceHubManager] instance.
 */
public open class ConfigurableCompositeControlComponent<D : ConfigurableCompositeControlComponent<D>>(
    public val spec: CompositeControlComponentSpec<D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
    config: DeviceLifecycleConfig = DeviceLifecycleConfig(),
    public val hubManager: DeviceHubManager = TODO(),
    private val externalConfigApplier: ExternalConfigApplier? = null
) : DeviceBase<D>(context, meta), CompositeControlComponent {

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = spec.properties

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = spec.actions

    override fun toString(): String = "Device(id=$id, spec=$spec)"

    override val devices: Map<Name, Device>
        get() = hubManager.devices

    protected val childConfigs: List<ChildComponentConfig<*>> = spec.childSpecs.values.toList()

    private val childInitializationStatus = mutableMapOf<Name, Boolean>()
    private val initLock = Mutex()

    init {
        hubManager.launchGlobal {
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
                            hubManager.magixEndpoint.send(
                                DeviceControlsFormats.DEVICE_MESSAGE_FORMAT,
                                resultMessage,
                                hubManager.sourceEndpoint
                            )
                        } catch (ex: Exception) {
                            logger.error(ex) { "Error executing action ${actionSpec.name} on device $id" }
                            hubManager.magixEndpoint.send(
                                DeviceControlsFormats.DEVICE_MESSAGE_FORMAT,
                                DeviceMessage.error(ex, id.asName()),
                                hubManager.sourceEndpoint
                            )
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    /**
     * Instantiates (and synchronously adds) all child devices declared in [spec.childSpecs].
     * For child devices with [LifecycleMode.LINKED], the device is started automatically.
     * For [LifecycleMode.INDEPENDENT], the device is added but not started automatically.
     * For [LifecycleMode.LAZY], the device is added and started only upon explicit request.
     */
    public suspend fun initChildren() {
        for (childCfg in childConfigs) {
            if (initLock.withLock { childInitializationStatus[childCfg.name] == true }) {
                continue
            }

            val builder = DeviceLifecycleConfigBuilder().apply {
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
            }

            externalConfigApplier?.applyConfig(builder, childCfg.name)

            val updatedConfig = builder.build()

            try {
                val childSpec = childCfg.spec
                val childDevice: ConfigurableCompositeControlComponent<*> = if (childSpec is DeviceSpecification<*>) {
                    childSpec.deviceFactory(context, childCfg.meta ?: Meta.EMPTY)
                } else {
                    ConfigurableCompositeControlComponent(
                        childSpec,
                        context,
                        childCfg.meta ?: Meta.EMPTY,
                        updatedConfig,
                        hubManager,
                        externalConfigApplier
                    )
                }

                hubManager.attachDevice(childCfg.name, childDevice, updatedConfig, childCfg.meta, StartMode.NONE)
                initLock.withLock {
                    childInitializationStatus[childCfg.name] = true
                }
            } catch (e: Exception) {
                logger.error(e) { "Error initializing child device ${childCfg.name}" }
                initLock.withLock {
                    childInitializationStatus[childCfg.name] = false
                }
                if (updatedConfig.onError == ChildDeviceErrorHandler.PROPAGATE) {
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
        val childDevices = hubManager.devices.entries.filter { (name, device) ->
            device.lifecycleState == LifecycleState.INITIAL &&
                    initLock.withLock { childInitializationStatus[name] == true }
        }

        for ((name, device) in childDevices) {
            try {
                device.start()
            } catch (e: Exception) {
                logger.error(e) { "Error starting child device $name during parent start" }
                val childConfig = childConfigs.find { it.name == name }?.config
                if (childConfig?.onError == ChildDeviceErrorHandler.PROPAGATE) {
                    throw e
                }
                logger.warn { "Continuing despite error in child device $name (error policy: ${childConfig?.onError})" }
            }
        }
    }

    /**
     * Called when the device stops.
     */
    override suspend fun onStop() {
        val runningChildren = hubManager.devices.entries.filter { (_, device) ->
            device.lifecycleState == LifecycleState.STARTED
        }

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
        return hubManager.devices[name] as? CD ?: error("Child device $name not found or type mismatch.")
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
    } catch (e: TimeoutCancellationException) {
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
 * Extension to get MagixDeviceHubManager from context
 */
public val Context.magixDeviceHubManager: MagixDeviceHubManager
    get() = plugins[MagixDeviceHubManager] ?: MagixDeviceHubManager()
