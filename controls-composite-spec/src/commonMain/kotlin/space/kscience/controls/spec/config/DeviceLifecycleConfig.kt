package space.kscience.controls.spec.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.spec.model.ChildDeviceErrorHandler
import space.kscience.controls.spec.model.LifecycleMode
import space.kscience.controls.spec.utils.ParsingUtils
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for a device's lifecycle, including timeouts and error handling.
 *
 * @property lifecycleMode The [LifecycleMode] of the device.
 * @property messageBuffer Buffer size for the device's message flow.
 * @property startDelay Delay before starting the device.
 * @property startTimeout Timeout for starting the device. Nullable, system default ([DeviceHubConfig]) will be used if null.
 * @property stopTimeout Timeout for stopping the device. Nullable, system default ([DeviceHubConfig]) will be used if null.
 * @property coroutineScope Optional [CoroutineScope] for the device. If null, a scope will be created by the manager.
 * @property dispatcher Optional [CoroutineDispatcher] for the device's operations. If null, manager's default will be used.
 * @property onError The [ChildDeviceErrorHandler] strategy to apply if this device is a child and encounters an error.
 * @property restartPolicy The [RestartPolicy] to apply if [onError] is set to [ChildDeviceErrorHandler.RESTART].
 */
public data class DeviceLifecycleConfig(
    val lifecycleMode: LifecycleMode = LifecycleMode.LINKED,
    val messageBuffer: Int = MessageBusConfig.Factory.Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY,
    val startDelay: Duration = Duration.ZERO,
    val startTimeout: Duration? = Defaults.DEVICE_START_TIMEOUT,
    val stopTimeout: Duration? = Defaults.DEVICE_STOP_TIMEOUT,
    val coroutineScope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher? = null,
    val onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART,
    val restartPolicy: RestartPolicy = RestartPolicy.Factory.Defaults.DEFAULT
) {
    init {
        require(messageBuffer > 0) { "Message buffer size must be positive." }
        startTimeout?.let { require(!it.isNegative()) { "Start timeout must not be negative." } }
        stopTimeout?.let { require(!it.isNegative()) { "Stop timeout must not be negative." } }
    }

    /**
     * Converts this configuration object to a [Meta] representation.
     * Note: `coroutineScope`, `dispatcher` are not serialized
     * as they are runtime objects.
     */
    public fun toMeta(): Meta = Meta {
        "lifecycleMode" put lifecycleMode.name
        "messageBuffer" put messageBuffer
        "startDelay" put startDelay.toString()
        startTimeout?.let { "startTimeout" put it.toString() }
        stopTimeout?.let { "stopTimeout" put it.toString() }
        "onError" put onError.name
        "restartPolicy" put restartPolicy.toMeta()
    }

    public companion object Factory {
        public object Defaults {
            public val DEVICE_START_TIMEOUT: Duration = 30.seconds
            public val DEVICE_STOP_TIMEOUT: Duration = 10.seconds
            public val DEVICE_ACTION_EXECUTION_TIMEOUT: Duration = 60.seconds
        }

        /**
         * Creates a [DeviceLifecycleConfig] instance from a [Meta] object.
         * Note: `coroutineScope`, `dispatcher` cannot be deserialized
         * from Meta and must be set programmatically if needed.
         */
        public fun fromMeta(meta: Meta): DeviceLifecycleConfig {
            val lifecycleMode = meta["lifecycleMode"]?.string?.let {
                try { LifecycleMode.valueOf(it.uppercase()) } catch (_: Exception) { LifecycleMode.LINKED }
            } ?: LifecycleMode.LINKED

            val messageBuffer = meta["messageBuffer"].int ?: MessageBusConfig.Factory.Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY
            val startDelay = meta["startDelay"]?.string?.let { ParsingUtils.parseDurationOrNull(it) } ?: Duration.ZERO
            val startTimeout = meta["startTimeout"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }
            val stopTimeout = meta["stopTimeout"]?.string?.let { ParsingUtils.parseDurationOrNull(it) }

            val onError = meta["onError"]?.string?.let {
                try { ChildDeviceErrorHandler.valueOf(it.uppercase()) } catch (_: Exception) { ChildDeviceErrorHandler.RESTART }
            } ?: ChildDeviceErrorHandler.RESTART

            val restartPolicy = meta["restartPolicy"]?.let { RestartPolicy.fromMeta(it) }
                ?: RestartPolicy.Factory.Defaults.DEFAULT

            return DeviceLifecycleConfig(
                lifecycleMode = lifecycleMode,
                messageBuffer = messageBuffer,
                startDelay = startDelay,
                startTimeout = startTimeout,
                stopTimeout = stopTimeout,
                onError = onError,
                restartPolicy = restartPolicy
            )
        }
    }
}

/**
 * Builder class for constructing [DeviceLifecycleConfig] instances fluently.
 */
public class DeviceLifecycleConfigBuilder {
    public var lifecycleMode: LifecycleMode = LifecycleMode.LINKED
    public var messageBuffer: Int = MessageBusConfig.Factory.Defaults.DEVICE_MESSAGE_FLOW_BUFFER_CAPACITY
    public var startDelay: Duration = Duration.ZERO
    public var startTimeout: Duration? = DeviceLifecycleConfig.Factory.Defaults.DEVICE_START_TIMEOUT
    public var stopTimeout: Duration? = DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT
    public var coroutineScope: CoroutineScope? = null
    public var dispatcher: CoroutineDispatcher? = null
    public var onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART
    public var restartPolicy: RestartPolicy = RestartPolicy.Factory.Defaults.DEFAULT

    /** Sets linked lifecycle mode (child device is linked to parent). */
    public fun linkedMode(): DeviceLifecycleConfigBuilder = apply { lifecycleMode = LifecycleMode.LINKED }
    /** Sets independent lifecycle mode (child device is independent from parent). */
    public fun independentMode(): DeviceLifecycleConfigBuilder = apply { lifecycleMode = LifecycleMode.INDEPENDENT }

    /** Sets message buffer size. */
    public fun messageBuffer(size: Int): DeviceLifecycleConfigBuilder = apply {
        if (size <= 0) throw IllegalArgumentException("Message buffer size must be positive.")
        messageBuffer = size
    }

    /** Sets start delay. */
    public fun startDelay(delay: Duration): DeviceLifecycleConfigBuilder = apply { startDelay = delay }

    /** Helper class for configuring timeouts in a DSL style. */
    public class TimeoutConfig {
        public var start: Duration? = null
        public var stop: Duration? = null
    }

    /** Configures start and stop timeouts using a nested DSL. */
    public fun timeouts(block: TimeoutConfig.() -> Unit): DeviceLifecycleConfigBuilder = apply {
        val tc = TimeoutConfig().apply {
            start = this@DeviceLifecycleConfigBuilder.startTimeout
            stop = this@DeviceLifecycleConfigBuilder.stopTimeout
        }.apply(block)

        startTimeout = tc.start?.also {
            if (it.isNegative()) throw IllegalArgumentException("Start timeout must not be negative.")
        }
        stopTimeout = tc.stop?.also {
            if (it.isNegative()) throw IllegalArgumentException("Stop timeout must not be negative.")
        }
    }

    /** Sets start timeout. */
    public fun startTimeout(timeout: Duration?): DeviceLifecycleConfigBuilder = apply {
        timeout?.let { if (it.isNegative()) throw IllegalArgumentException("Start timeout must not be negative.") }
        startTimeout = timeout
    }

    /** Sets stop timeout. */
    public fun stopTimeout(timeout: Duration?): DeviceLifecycleConfigBuilder = apply {
        timeout?.let { if (it.isNegative()) throw IllegalArgumentException("Stop timeout must not be negative.") }
        stopTimeout = timeout
    }

    /** Sets the [CoroutineScope] for the device. */
    public fun coroutineScope(scope: CoroutineScope?): DeviceLifecycleConfigBuilder = apply { coroutineScope = scope }
    /** Sets the [CoroutineDispatcher] for the device. */
    public fun dispatcher(disp: CoroutineDispatcher?): DeviceLifecycleConfigBuilder = apply { dispatcher = disp }

    /** Helper class for configuring error handling in a DSL style. */
    public class ErrorHandlingConfig {
        public var strategy: ChildDeviceErrorHandler? = null
        public var policy: RestartPolicy? = null
    }

    /** Configures error handling strategy and restart policy using a nested DSL. */
    public fun errorHandling(block: ErrorHandlingConfig.() -> Unit): DeviceLifecycleConfigBuilder = apply {
        val ehc = ErrorHandlingConfig().apply {
            strategy = this@DeviceLifecycleConfigBuilder.onError
            policy = this@DeviceLifecycleConfigBuilder.restartPolicy
        }.apply(block)

        ehc.strategy?.let { onError = it }
        ehc.policy?.let { restartPolicy = it }
    }

    /** Sets the [RestartPolicy] directly. */
    public fun restartPolicy(policy: RestartPolicy): DeviceLifecycleConfigBuilder = apply {
        this.restartPolicy = policy
    }

    /** Configures the [RestartPolicy] using a nested DSL via [RestartPolicyBuilder]. */
    public fun restartPolicy(block: RestartPolicyBuilder.() -> Unit): DeviceLifecycleConfigBuilder = apply {
        val builder = RestartPolicyBuilder(this.restartPolicy) // Initialize with current policy
        builder.apply(block)
        this.restartPolicy = builder.build()
    }

    /** Builds the [DeviceLifecycleConfig] instance. */
    public fun build(): DeviceLifecycleConfig = DeviceLifecycleConfig(
        lifecycleMode, messageBuffer, startDelay, startTimeout, stopTimeout,
        coroutineScope, dispatcher, onError, restartPolicy
    )

    public companion object {
        /** Creates a new [DeviceLifecycleConfigBuilder]. */
        public fun builder(): DeviceLifecycleConfigBuilder = DeviceLifecycleConfigBuilder()
    }
}

/**
 * Builder for creating [RestartPolicy] instances fluently, typically used within
 * the [DeviceLifecycleConfigBuilder] DSL.
 *
 * @param initialPolicy An optional initial [RestartPolicy] to pre-fill the builder.
 */
public class RestartPolicyBuilder internal constructor(initialPolicy: RestartPolicy?) {
    public var maxAttempts: Int = initialPolicy?.maxAttempts ?: RestartPolicy.Factory.Defaults.DEFAULT_RESTART_POLICY_MAX_ATTEMPTS
    public var delayBetweenAttempts: Duration = initialPolicy?.delayBetweenAttempts ?: RestartPolicy.Factory.Defaults.DEFAULT_RESTART_POLICY_DELAY
    public var resetOnSuccess: Boolean = initialPolicy?.resetOnSuccess ?: true
    public var strategy: RestartStrategy = initialPolicy?.strategy ?: RestartStrategy.Linear
    public var circuitBreaker: CircuitBreakerConfig? = initialPolicy?.circuitBreaker

    /** Sets the restart strategy to [RestartStrategy.Linear]. */
    public fun linearStrategy() { strategy = RestartStrategy.Linear }
    /** Sets the restart strategy to [RestartStrategy.ExponentialBackoff]. */
    public fun exponentialBackoffStrategy() { strategy = RestartStrategy.ExponentialBackoff }
    /** Sets the restart strategy to [RestartStrategy.Fibonacci]. */
    public fun fibonacciStrategy() { strategy = RestartStrategy.Fibonacci }

    /** Helper class for configuring [CircuitBreakerConfig] in a DSL style. */
    public class CircuitBreakerPolicyConfigBuilder internal constructor(initialCbConfig: CircuitBreakerConfig?) {
        public var failureThreshold: Int = initialCbConfig?.failureThreshold ?: CircuitBreakerConfig.Factory.Defaults.DEFAULT_FAILURE_THRESHOLD
        public var resetTimeout: Duration = initialCbConfig?.resetTimeout ?: CircuitBreakerConfig.Factory.Defaults.DEFAULT_RESET_TIMEOUT
        public var additionalTimeAfterFailure: Duration = initialCbConfig?.additionalTimeAfterFailure ?: CircuitBreakerConfig.Factory.Defaults.DEFAULT_ADDITIONAL_TIME_AFTER_FAILURE

        internal fun build(): CircuitBreakerConfig = CircuitBreakerConfig(
            failureThreshold, resetTimeout, additionalTimeAfterFailure
        )
    }

    /** Enables and configures the circuit breaker using a nested DSL. */
    public fun circuitBreaker(block: CircuitBreakerPolicyConfigBuilder.() -> Unit = {}): RestartPolicyBuilder = apply {
        val builder = CircuitBreakerPolicyConfigBuilder(this.circuitBreaker)
        builder.apply(block)
        this.circuitBreaker = builder.build()
    }

    /** Disables the circuit breaker for this restart policy. */
    public fun noCircuitBreaker(): RestartPolicyBuilder = apply {
        this.circuitBreaker = null
    }

    internal fun build(): RestartPolicy = RestartPolicy(
        maxAttempts, delayBetweenAttempts, resetOnSuccess, strategy, circuitBreaker
    )
}