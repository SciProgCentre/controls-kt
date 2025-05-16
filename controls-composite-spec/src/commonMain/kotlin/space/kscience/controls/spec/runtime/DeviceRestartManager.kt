package space.kscience.controls.spec.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.DeviceNotFoundException
import space.kscience.controls.api.DeviceStartupException
import space.kscience.controls.spec.model.* // Imports exceptions, LifecycleMode etc.
import space.kscience.controls.spec.infra.MessagingSystem
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.controls.spec.utils.timeSourceOrDefault
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.names.Name
import kotlin.time.Duration

/**
 * Manages device restart operations, applying restart policies and interacting with the [CircuitBreakerManager].
 */
public class DeviceRestartManager(
    private val context: Context,
    private val registry: DeviceRegistry,
    private val lifecycleManager: DeviceLifecycleManager,
    private val circuitBreakerManager: CircuitBreakerManager,
    private val messagingSystem: MessagingSystem,
    private val timeSource: TimeSource = context.timeSourceOrDefault,
    private val logger: Logger = context.logger
) {
    private data class RestartState(var attempts: Int = 0, var isRestarting: Boolean = false)

    private val restartStates = mutableMapOf<Name, RestartState>()
    private val restartStateLock = Mutex()

    public suspend fun restartDevice(name: Name): Boolean {
        val initialDeviceJob = registry.getDeviceJob(name)
            ?: throw DeviceNotFoundException("Device '$name' not found, cannot perform restart.")

        val policy = initialDeviceJob.config.restartPolicy
        var attemptForThisRun: Int
        var isCurrentAttemptHalfOpen = false

        restartStateLock.withLock {
            val state = restartStates.getOrPut(name) { RestartState() }
            if (state.isRestarting) {
                logger.warn { "Device '$name' restart skipped: Already in process of restarting." }
                messagingSystem.incrementCounter(
                    "$name.restart.rejected",
                    tags = mapOf("device_name" to name.toString(), "reason" to "already_restarting")
                )
                return false
            }

            if (!circuitBreakerManager.shouldAttemptRestart(name, policy)) {
                val cbStatus = circuitBreakerManager.getCircuitBreakerStatus(name)
                if (cbStatus?.get("halfOpenAttemptInProgress") == true) {
                    isCurrentAttemptHalfOpen = true
                    logger.info { "Circuit breaker for '$name' is HALF-OPEN. Permitting one restart attempt." }
                } else {
                    logger.warn { "Circuit breaker denied restart attempt for '$name' (state: OPEN)." }
                    messagingSystem.incrementCounter(
                        "$name.restart.rejected",
                        tags = mapOf("device_name" to name.toString(), "reason" to "circuit_breaker_open")
                    )
                    return false
                }
            }
            state.isRestarting = true
            state.attempts++
            attemptForThisRun = state.attempts
        }

        var success = false
        try {
            val restartStartTime = timeSource.now()
            messagingSystem.incrementCounter(
                "$name.restart.attempt",
                tags = mapOf("device_name" to name.toString(), "attempt" to attemptForThisRun.toString(), "half_open" to isCurrentAttemptHalfOpen.toString())
            )

            if (attemptForThisRun > policy.maxAttempts && !isCurrentAttemptHalfOpen) {
                logger.warn { "Max restart attempts (${policy.maxAttempts}) exceeded for '$name'. Current: $attemptForThisRun." }
                messagingSystem.incrementCounter(
                    "$name.restart.max_attempts_exceeded",
                    tags = mapOf("device_name" to name.toString())
                )
                if (!isCurrentAttemptHalfOpen) circuitBreakerManager.recordRestartFailure(name, policy)
            } else {
                if (!isCurrentAttemptHalfOpen) {
                    val delayDuration = lifecycleManager.calculateRestartDelay(policy, attemptForThisRun)
                    if (delayDuration > Duration.ZERO) {
                        logger.info { "Delaying restart of '$name' by $delayDuration (Attempt $attemptForThisRun)." }
                        messagingSystem.recordDuration(
                            "$name.restart.delay", delayDuration, name,
                            mapOf("device_name" to name.toString(), "attempt" to attemptForThisRun.toString())
                        )
                        timeSource.delay(delayDuration)
                    }
                }

                val attemptType = if (isCurrentAttemptHalfOpen) "HALF-OPEN" else "NORMAL"
                logger.info { "Executing $attemptType restart for device '$name' (Attempt $attemptForThisRun)." }
                try {
                    lifecycleManager.detachDevice(name, waitStop = true)
                    lifecycleManager.attachDevice(
                        name, initialDeviceJob.device, initialDeviceJob.config, initialDeviceJob.meta, StartMode.NONE
                    )
                    val newDeviceJob = registry.getDeviceJob(name)
                        ?: throw DeviceStartupException("Failed to re-register device '$name' during restart.")

                    if (newDeviceJob.config.lifecycleMode != LifecycleMode.INDEPENDENT) {
                        val startOpTime = timeSource.now()
                        lifecycleManager.startDevice(name, newDeviceJob.config, newDeviceJob.device)
                        messagingSystem.recordDuration(
                            "$name.restart.start_duration", timeSource.now() - startOpTime, name,
                            mapOf("device_name" to name.toString())
                        )
                    }
                    success = true
                } catch (restartEx: Exception) {
                    logger.error(restartEx) { "Core restart logic failed for '$name' (Attempt $attemptForThisRun, Type: $attemptType)." }
                    messagingSystem.incrementCounter(
                        "$name.restart.core_failure",
                        tags = mapOf("device_name" to name.toString(), "error_type" to (restartEx::class.simpleName ?: "unknown"))
                    )
                }
            }

            if (success) {
                if (policy.resetOnSuccess && !isCurrentAttemptHalfOpen) {
                    restartStateLock.withLock { restartStates[name]?.attempts = 0 }
                }
                if (!isCurrentAttemptHalfOpen) circuitBreakerManager.recordRestartSuccess(name)

                messagingSystem.recordDuration(
                    "$name.restart.total_duration", timeSource.now() - restartStartTime, name,
                    mapOf("device_name" to name.toString())
                )
                messagingSystem.incrementCounter("$name.restart.success", tags = mapOf("device_name" to name.toString()))
                messagingSystem.logDevice("Device '$name' successfully restarted.", name)
            } else {
                if (!isCurrentAttemptHalfOpen) circuitBreakerManager.recordRestartFailure(name, policy)

                messagingSystem.incrementCounter("$name.restart.failure", tags = mapOf("device_name" to name.toString()))
                messagingSystem.logDevice("Failed to restart device '$name' (Attempt $attemptForThisRun).", name)
            }
            return success
        } finally {
            if (isCurrentAttemptHalfOpen) {
                try {
                    circuitBreakerManager.concludeHalfOpenAttempt(name, success)
                } catch (concludeEx: Exception) {
                    logger.error(concludeEx) { "Error concluding half-open attempt for '$name'." }
                }
            }
            restartStateLock.withLock { restartStates[name]?.isRestarting = false }
        }
    }

    public suspend fun resetRestartAttempts(deviceName: Name) {
        restartStateLock.withLock { restartStates.remove(deviceName) }
        circuitBreakerManager.resetCircuitBreaker(deviceName)
        logger.info { "Restart attempts and circuit breaker state for device '$deviceName' manually reset." }
        messagingSystem.logSystem("Restart attempts and CB for '$deviceName' reset.", "DeviceRestartManager")
    }

    public suspend fun getRestartAttemptCount(deviceName: Name): Int =
        restartStateLock.withLock { restartStates[deviceName]?.attempts ?: 0 }

    public suspend fun cleanup() {
        val registeredDeviceNames = registry.getDeviceNames()
        var cleanedCount = 0
        restartStateLock.withLock {
            val initialSize = restartStates.size
            restartStates.entries.removeAll { (key, state) ->
                val shouldRemove = !state.isRestarting && key !in registeredDeviceNames
                if (shouldRemove) {
                    logger.debug { "Cleaning stale restart state for unregistered device '$key'." }
                }
                shouldRemove
            }
            cleanedCount = initialSize - restartStates.size
        }
        if (cleanedCount > 0) {
            logger.info { "Cleaned $cleanedCount stale restart attempt entries." }
        }
    }
}
