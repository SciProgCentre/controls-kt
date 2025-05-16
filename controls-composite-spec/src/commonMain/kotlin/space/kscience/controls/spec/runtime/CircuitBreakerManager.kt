package space.kscience.controls.spec.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.spec.config.CircuitBreakerConfig
import space.kscience.controls.spec.config.RestartPolicy
import space.kscience.controls.spec.infra.MessagingSystem
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.controls.spec.utils.timeSourceOrDefault
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.names.Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents the state of a circuit breaker for a device.
 *
 * @property config The configuration for this circuit breaker.
 * @property timeSource The time source used for timestamps.
 * @property failureCount Current number of consecutive failures.
 * @property openSince Timestamp (epoch milliseconds) when the circuit was opened (0 if closed or never opened).
 * @property isOpen Whether the circuit is currently open.
 * @property lastAccessed Timestamp (epoch milliseconds) of the last access or state change, used for cleanup.
 * @property halfOpenAttemptInProgress True if a restart attempt is in progress while the circuit was half-open.
 */
public data class CircuitBreakerState(
    val config: CircuitBreakerConfig,
    internal val timeSource: TimeSource,
    var failureCount: Int = 0,
    var openSince: Long = 0,
    var isOpen: Boolean = false,
    var lastAccessed: Long = timeSource.now().toEpochMilliseconds(),
    var halfOpenAttemptInProgress: Boolean = false
) {
    public constructor(config: CircuitBreakerConfig, timeSource: TimeSource) : this(
        config = config,
        timeSource = timeSource,
        lastAccessed = timeSource.now().toEpochMilliseconds()
    )
}


/**
 * Manages circuit breaker patterns for device fault tolerance.
 */
public class CircuitBreakerManager(
    context: Context,
    private val registry: DeviceRegistry,
    private val messagingSystem: MessagingSystem,
    private val timeSource: TimeSource = context.timeSourceOrDefault,
    private val logger: Logger = context.logger
) {
    private val circuitBreakerStates = mutableMapOf<Name, CircuitBreakerState>()
    private val lock = Mutex()

    public suspend fun shouldAttemptRestart(deviceName: Name, policy: RestartPolicy): Boolean {
        val circuitBreakerConfig = policy.circuitBreaker ?: return true

        return lock.withLock {
            val state = circuitBreakerStates.getOrPut(deviceName) {
                CircuitBreakerState(circuitBreakerConfig, timeSource)
            }
            state.lastAccessed = timeSource.now().toEpochMilliseconds()

            if (state.halfOpenAttemptInProgress) {
                logger.debug { "Circuit breaker for '$deviceName': Half-open attempt already in progress. Denying." }
                return@withLock false
            }

            if (state.isOpen) {
                val nowMs = timeSource.now().toEpochMilliseconds()
                val timeInOpenStateMs = nowMs - state.openSince
                val baseResetTimeoutMs = circuitBreakerConfig.resetTimeout.inWholeMilliseconds
                val additionalFailures = (state.failureCount - circuitBreakerConfig.failureThreshold).coerceAtLeast(0)
                val additionalBackoffMs = additionalFailures * circuitBreakerConfig.additionalTimeAfterFailure.inWholeMilliseconds
                val effectiveResetTimeoutMs = baseResetTimeoutMs + additionalBackoffMs

                if (timeInOpenStateMs >= effectiveResetTimeoutMs) {
                    state.halfOpenAttemptInProgress = true
                    messagingSystem.incrementCounter(
                        "device.circuit_breaker.half_open",
                        tags = mapOf("device_name" to deviceName.toString())
                    )
                    logger.info { "Circuit breaker for '$deviceName': Reset timeout ($effectiveResetTimeoutMs ms) expired. Transitioning to HALF-OPEN." }
                    true
                } else {
                    messagingSystem.incrementCounter(
                        "device.circuit_breaker.reject",
                        tags = mapOf("device_name" to deviceName.toString())
                    )
                    logger.debug { "Circuit breaker for '$deviceName' is OPEN, restart rejected. Time left for reset: ${(effectiveResetTimeoutMs - timeInOpenStateMs).milliseconds}." }
                    false
                }
            } else {
                true
            }
        }
    }

    public suspend fun recordRestartFailure(deviceName: Name, policy: RestartPolicy) {
        val circuitBreakerConfig = policy.circuitBreaker ?: return

        lock.withLock {
            val state = circuitBreakerStates.getOrPut(deviceName) {
                CircuitBreakerState(circuitBreakerConfig, timeSource)
            }
            state.lastAccessed = timeSource.now().toEpochMilliseconds()
            state.failureCount++

            if (state.halfOpenAttemptInProgress) {
                logger.warn { "Circuit breaker for '$deviceName': Failure recorded during half-open attempt. Count: ${state.failureCount}."}
            } else if (!state.isOpen && state.failureCount >= circuitBreakerConfig.failureThreshold) {
                state.isOpen = true
                state.openSince = timeSource.now().toEpochMilliseconds()
                messagingSystem.incrementCounter(
                    "device.circuit_breaker.open",
                    tags = mapOf("device_name" to deviceName.toString())
                )
                logger.warn { "Circuit breaker for '$deviceName' OPENED after ${state.failureCount} failures (threshold: ${circuitBreakerConfig.failureThreshold})." }
            } else if (state.isOpen) {
                state.openSince = timeSource.now().toEpochMilliseconds()
                logger.warn { "Circuit breaker for '$deviceName' remains OPEN after another failure. Total failures: ${state.failureCount}." }
            }
        }
    }

    public suspend fun recordRestartSuccess(deviceName: Name) {
        val policy = registry.getDeviceJob(deviceName)?.config?.restartPolicy
        if (policy?.circuitBreaker == null && !lock.withLock { circuitBreakerStates.containsKey(deviceName) }) {
            return
        }

        lock.withLock {
            circuitBreakerStates[deviceName]?.let { state ->
                if (state.isOpen || state.failureCount > 0) {
                    logger.info { "Circuit breaker for '$deviceName': Resetting. Previous state: isOpen=${state.isOpen}, failures=${state.failureCount}." }
                    messagingSystem.incrementCounter(
                        "device.circuit_breaker.reset",
                        tags = mapOf("device_name" to deviceName.toString())
                    )
                }
                state.failureCount = 0
                state.isOpen = false
                state.openSince = 0
                state.lastAccessed = timeSource.now().toEpochMilliseconds()
            }
        }
    }

    internal suspend fun concludeHalfOpenAttempt(deviceName: Name, success: Boolean) {
        lock.withLock {
            val state = circuitBreakerStates[deviceName] ?: run {
                logger.warn { "concludeHalfOpenAttempt called for '$deviceName', but no CB state found." }
                return@withLock
            }

            if (!state.halfOpenAttemptInProgress) {
                logger.debug { "concludeHalfOpenAttempt called for '$deviceName', but no attempt was marked in progress. Current: isOpen=${state.isOpen}, failures=${state.failureCount}." }
                if (success && (state.isOpen || state.failureCount > 0)) {
                    logger.info { "Circuit breaker for '$deviceName': Half-open attempt concluded (no prior mark), resetting from state isOpen=${state.isOpen}, failures=${state.failureCount}."}
                    state.failureCount = 0
                    state.isOpen = false
                    state.openSince = 0
                    messagingSystem.incrementCounter(
                        "device.circuit_breaker.reset_from_half_open",
                        tags = mapOf("device_name" to deviceName.toString())
                    )
                }
                return@withLock
            }

            state.halfOpenAttemptInProgress = false
            state.lastAccessed = timeSource.now().toEpochMilliseconds()

            if (success) {
                if (state.isOpen || state.failureCount > 0) {
                    logger.info { "Circuit breaker for '$deviceName': Half-open restart SUCCEEDED. Resetting. Failures before reset: ${state.failureCount}." }
                    messagingSystem.incrementCounter(
                        "device.circuit_breaker.reset_from_half_open",
                        tags = mapOf("device_name" to deviceName.toString())
                    )
                }
                state.failureCount = 0
                state.isOpen = false
                state.openSince = 0
            } else {
                state.isOpen = true
                state.openSince = timeSource.now().toEpochMilliseconds()
                logger.warn { "Circuit breaker for '$deviceName': Half-open restart FAILED. Circuit remains/becomes OPEN. Failures: ${state.failureCount}." }
                messagingSystem.incrementCounter(
                    "device.circuit_breaker.re_trip_half_open",
                    tags = mapOf("device_name" to deviceName.toString())
                )
            }
        }
    }

    public suspend fun getCircuitBreakerStatus(deviceName: Name): Map<String, Any>? = lock.withLock {
        circuitBreakerStates[deviceName]?.let { state ->
            mapOf(
                "isOpen" to state.isOpen,
                "failureCount" to state.failureCount,
                "openSinceEpochMs" to state.openSince,
                "lastAccessedEpochMs" to state.lastAccessed,
                "configFailureThreshold" to state.config.failureThreshold,
                "configResetTimeoutMs" to state.config.resetTimeout.inWholeMilliseconds,
                "configAdditionalTimeAfterFailureMs" to state.config.additionalTimeAfterFailure.inWholeMilliseconds,
                "halfOpenAttemptInProgress" to state.halfOpenAttemptInProgress
            )
        }
    }

    public suspend fun resetCircuitBreaker(deviceName: Name) {
        lock.withLock {
            if (circuitBreakerStates.remove(deviceName) != null) {
                logger.info { "Circuit breaker for '$deviceName' manually reset." }
                messagingSystem.incrementCounter(
                    "device.circuit_breaker.manual_reset",
                    tags = mapOf("device_name" to deviceName.toString())
                )
            } else {
                logger.info { "Manual reset for '$deviceName' requested, but no CB state found." }
            }
        }
    }

    /**
     * Cleans up stale circuit breaker states.
     *
     * @param maxIdleTime Time after which a non-open, non-half-open state is removed.
     * @param maxIdleTimeOpen Time after which an open or half-open state is removed (typically longer).
     */
    public suspend fun cleanup(maxIdleTime: Duration, maxIdleTimeOpen: Duration = maxIdleTime * 5) {
        val nowMs = timeSource.now().toEpochMilliseconds()
        val maxIdleMs = maxIdleTime.inWholeMilliseconds
        val maxIdleOpenMs = maxIdleTimeOpen.inWholeMilliseconds.coerceAtLeast(maxIdleMs) // Ensure open idle time is at least normal idle time
        var cleanedCount = 0

        lock.withLock {
            val initialSize = circuitBreakerStates.size
            circuitBreakerStates.entries.removeAll { (name, state) ->
                val idleTimeMs = nowMs - state.lastAccessed
                val shouldRemove = if (state.isOpen || state.halfOpenAttemptInProgress) {
                    idleTimeMs > maxIdleOpenMs
                } else {
                    idleTimeMs > maxIdleMs
                }
                if (shouldRemove) {
                    logger.debug { "Cleaning up CB state for '$name' (isOpen=${state.isOpen}, halfOpenInProgress=${state.halfOpenAttemptInProgress}, idle=${idleTimeMs}ms)." }
                }
                shouldRemove
            }
            cleanedCount = initialSize - circuitBreakerStates.size
        }

        if (cleanedCount > 0) {
            logger.info { "Cleaned up $cleanedCount stale circuit breaker states." }
        }
    }
}