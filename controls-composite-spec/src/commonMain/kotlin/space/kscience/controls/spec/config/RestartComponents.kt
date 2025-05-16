package space.kscience.controls.spec.config

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.meta.string
import space.kscience.controls.spec.utils.ParsingUtils
import space.kscience.dataforge.meta.boolean
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Strategy for calculating delay between restart attempts.
 */
public sealed interface RestartStrategy {
    /** Calculates delay based on base delay and attempt number. */
    public fun calculateDelay(baseDelay: Duration, attempt: Int): Duration

    /** Linear strategy - always the same delay. */
    public object Linear : RestartStrategy {
        override fun calculateDelay(baseDelay: Duration, attempt: Int): Duration = baseDelay
    }

    /** Exponential strategy - delay grows exponentially with each attempt. */
    public object ExponentialBackoff : RestartStrategy {
        override fun calculateDelay(baseDelay: Duration, attempt: Int): Duration =
            baseDelay * 2.0.pow((attempt - 1).coerceAtLeast(0).toDouble())
    }

    /** Fibonacci strategy - delay follows the Fibonacci sequence. */
    public object Fibonacci : RestartStrategy {
        private val fibCache = mutableMapOf<Int, Long>()

        private fun fib(n: Int): Long {
            if (n <= 0) return 0L
            if (n == 1) return 1L
            if (n <= 20) {
                var a = 0L
                var b = 1L
                repeat(n -1) {
                    val sum = a + b
                    a = b
                    b = sum
                }
                return b
            }
            return fibCache.getOrPut(n) { fib(n - 1) + fib(n - 2) }
        }

        override fun calculateDelay(baseDelay: Duration, attempt: Int): Duration =
            if (attempt <= 0) Duration.ZERO else baseDelay * fib(attempt).toDouble()
    }
}

/**
 * Circuit Breaker configuration for resilient failure recovery.
 *
 * @property failureThreshold Consecutive failures threshold before opening the circuit.
 * @property resetTimeout Time in open state before automatic transition to half-open.
 * @property additionalTimeAfterFailure Additional backoff time added to [resetTimeout] for each failure
 *                                      that occurred while the circuit was already open or during half-open attempts,
 *                                      making recovery progressively slower for persistently failing services.
 */
public data class CircuitBreakerConfig(
    val failureThreshold: Int = Defaults.DEFAULT_FAILURE_THRESHOLD,
    val resetTimeout: Duration = Defaults.DEFAULT_RESET_TIMEOUT,
    val additionalTimeAfterFailure: Duration = Defaults.DEFAULT_ADDITIONAL_TIME_AFTER_FAILURE
) {
    /**
     * Converts this configuration object to a [Meta] representation.
     */
    public fun toMeta(): Meta = Meta {
        "failureThreshold" put failureThreshold
        "resetTimeout" put resetTimeout.toString()
        "additionalTimeAfterFailure" put additionalTimeAfterFailure.toString()
    }

    public companion object Factory {
        public object Defaults {
            public const val DEFAULT_FAILURE_THRESHOLD: Int = 5
            public val DEFAULT_RESET_TIMEOUT: Duration = 60.seconds
            public val DEFAULT_ADDITIONAL_TIME_AFTER_FAILURE: Duration = 30.seconds
        }

        /**
         * Creates a [CircuitBreakerConfig] instance from a [Meta] object.
         */
        public fun fromMeta(meta: Meta): CircuitBreakerConfig = CircuitBreakerConfig(
            failureThreshold = meta["failureThreshold"].int ?: Defaults.DEFAULT_FAILURE_THRESHOLD,
            resetTimeout = meta["resetTimeout"]?.string?.let { ParsingUtils.parseDurationOrNull(it) } ?: Defaults.DEFAULT_RESET_TIMEOUT,
            additionalTimeAfterFailure = meta["additionalTimeAfterFailure"]?.string?.let { ParsingUtils.parseDurationOrNull(it) } ?: Defaults.DEFAULT_ADDITIONAL_TIME_AFTER_FAILURE
        )
    }
}


/**
 * Data class describing restart behavior for a device.
 *
 * @property maxAttempts Maximum number of restart attempts. Use [Int.MAX_VALUE] for unlimited.
 * @property delayBetweenAttempts Base delay between restart attempts. Specific [RestartStrategy] may modify this.
 * @property resetOnSuccess Whether to reset the restart attempt counter on a successful start.
 * @property strategy The [RestartStrategy] for calculating delay between attempts.
 * @property circuitBreaker Optional [CircuitBreakerConfig] to apply circuit breaker pattern.
 */
public data class RestartPolicy(
    val maxAttempts: Int = Defaults.DEFAULT_RESTART_POLICY_MAX_ATTEMPTS,
    val delayBetweenAttempts: Duration = Defaults.DEFAULT_RESTART_POLICY_DELAY,
    val resetOnSuccess: Boolean = true,
    val strategy: RestartStrategy = RestartStrategy.Linear,
    val circuitBreaker: CircuitBreakerConfig? = null
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive." }
        require(!delayBetweenAttempts.isNegative()) { "delayBetweenAttempts must not be negative." }
    }

    /**
     * Converts this configuration object to a [Meta] representation.
     */
    public fun toMeta(): Meta = Meta {
        "maxAttempts" put maxAttempts
        "delayBetweenAttempts" put delayBetweenAttempts.toString()
        "resetOnSuccess" put resetOnSuccess
        strategy::class.simpleName?.let { "strategy" put it }
        circuitBreaker?.let { "circuitBreaker" put it.toMeta() }
    }

    public companion object Factory {
        public object Defaults {
            public const val DEFAULT_RESTART_POLICY_MAX_ATTEMPTS: Int = 5
            public val DEFAULT_RESTART_POLICY_DELAY: Duration = 2.seconds

            /** Standard restart policy with sensible defaults. */
            public val DEFAULT: RestartPolicy = RestartPolicy(
                DEFAULT_RESTART_POLICY_MAX_ATTEMPTS,
                DEFAULT_RESTART_POLICY_DELAY
            )

            /** Example policy with circuit breaker enabled. */
            public val WITH_CIRCUIT_BREAKER: RestartPolicy = RestartPolicy(
                maxAttempts = 3,
                delayBetweenAttempts = 5.seconds,
                strategy = RestartStrategy.ExponentialBackoff,
                circuitBreaker = CircuitBreakerConfig(failureThreshold = 3, resetTimeout = 30.seconds)
            )

            /** Example policy using Fibonacci backoff and circuit breaker. */
            public val FIBONACCI_WITH_CIRCUIT_BREAKER: RestartPolicy = RestartPolicy(
                maxAttempts = 8,
                delayBetweenAttempts = 1.seconds,
                strategy = RestartStrategy.Fibonacci,
                circuitBreaker = CircuitBreakerConfig(failureThreshold = 5, resetTimeout = 2.minutes)
            )
        }

        /**
         * Creates a [RestartPolicy] instance from a [Meta] object.
         */
        public fun fromMeta(meta: Meta): RestartPolicy {
            val maxAttempts = meta["maxAttempts"].int ?: Defaults.DEFAULT_RESTART_POLICY_MAX_ATTEMPTS
            val delayBetweenAttempts = meta["delayBetweenAttempts"]?.string?.let { ParsingUtils.parseDurationOrNull(it) } ?: Defaults.DEFAULT_RESTART_POLICY_DELAY
            val resetOnSuccess = meta["resetOnSuccess"]?.boolean ?: true

            val strategyName = meta["strategy"].string
            val strategy = when (strategyName) {
                "Linear" -> RestartStrategy.Linear
                "ExponentialBackoff" -> RestartStrategy.ExponentialBackoff
                "Fibonacci" -> RestartStrategy.Fibonacci
                else -> RestartStrategy.Linear
            }

            val circuitBreaker = meta["circuitBreaker"]?.let { CircuitBreakerConfig.fromMeta(it) }

            return RestartPolicy(
                maxAttempts = maxAttempts,
                delayBetweenAttempts = delayBetweenAttempts,
                resetOnSuccess = resetOnSuccess,
                strategy = strategy,
                circuitBreaker = circuitBreaker
            )
        }
    }
}