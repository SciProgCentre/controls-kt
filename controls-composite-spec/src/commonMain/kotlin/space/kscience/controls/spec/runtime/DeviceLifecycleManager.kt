package space.kscience.controls.spec.runtime

import kotlinx.coroutines.*
import space.kscience.controls.api.*
import space.kscience.controls.spec.config.DeviceHubConfig
import space.kscience.controls.spec.config.DeviceLifecycleConfig
import space.kscience.controls.spec.infra.MessagingSystem
import space.kscience.controls.spec.model.*
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.controls.spec.utils.deviceManagerConfig
import space.kscience.controls.spec.config.RestartPolicy
import space.kscience.controls.spec.utils.timeSourceOrDefault
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.time.Duration

/**
 * Manages the lifecycle of devices, including attachment, detachment, start, and stop operations.
 * It interacts with a [DeviceRegistry] and a [MessagingSystem].
 */
public class DeviceLifecycleManager(
    private val context: Context,
    private val registry: DeviceRegistry,
    private val messagingSystem: MessagingSystem,
    internal val timeSource: TimeSource = context.timeSourceOrDefault,
    private val logger: Logger = context.logger
) {
    private val hubConfig: DeviceHubConfig by lazy { context.deviceManagerConfig }

    /**
     * Attaches a device to the system.
     */
    public suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        startMode: StartMode = StartMode.NONE
    ) {
        if (registry.containsDevice(name)) {
            throw DeviceConfigurationException("Device '$name' already exists in the registry.")
        }

        registry.registerDevice(name, device, config, meta) { message ->
            messagingSystem.publish(message)
        }

        messagingSystem.publish(messagingSystem.messageFactory.deviceAdded(name.toString()))
        logger.info { "Device '$name' attached with startMode=$startMode, lifecycleMode=${config.lifecycleMode}." }

        if (config.lifecycleMode == LifecycleMode.INDEPENDENT && (startMode == StartMode.ASYNC || startMode == StartMode.SYNC)) {
            logger.info { "Device '$name' is INDEPENDENT; explicit start via startMode=$startMode is ignored. Device must be started manually if needed." }
            return
        }

        if (config.lifecycleMode == LifecycleMode.LINKED) {
            when (startMode) {
                StartMode.NONE -> Unit
                StartMode.ASYNC -> context.launch(CoroutineName("AsyncStart-$name")) {
                    try {
                        startDevice(name)
                    } catch (e: Exception) {
                        logger.error(e) { "Async start for device '$name' failed." }
                    }
                }
                StartMode.SYNC -> startDevice(name)
            }
        }
    }

    /**
     * Starts a registered device.
     */
    public suspend fun startDevice(
        name: Name,
        configOverride: DeviceLifecycleConfig? = null,
        deviceOverride: Device? = null
    ) {
        val job = registry.getDeviceJob(name)
            ?: throw DeviceNotFoundException("Device '$name' not found for start operation.")

        val configToUse = configOverride ?: job.config
        val deviceToUse = deviceOverride ?: job.device
        val deviceScope = job.deviceScope

        val lifeCycleDevice = deviceToUse as? WithLifeCycle
        val state = lifeCycleDevice?.lifecycleState ?: LifecycleState.UNKNOWN

        if (state == LifecycleState.STARTED) {
            logger.warn { "Device '$name' is already started." }
            return
        }
        if (state != LifecycleState.INITIAL && state != LifecycleState.STOPPED && state != LifecycleState.UNKNOWN) {
            throw DeviceStateTransitionException("Cannot start device '$name' from state $state.")
        }

        if (lifeCycleDevice == null) {
            logger.warn { "Device '$name' does not implement WithLifeCycle; cannot be explicitly started. Assuming operational." }
            if (state == LifecycleState.UNKNOWN) {
                messagingSystem.publish(messagingSystem.messageFactory.deviceStarted(name.toString()))
            }
            return
        }

        if (configToUse.startDelay > Duration.ZERO) {
            logger.info { "Delaying start of '$name' by ${configToUse.startDelay}." }
            timeSource.delay(configToUse.startDelay)
        }

        val startTime = timeSource.now()
        val startTimeoutDuration = configToUse.startTimeout ?: hubConfig.defaultStartTimeout

        try {
            withTimeout(startTimeoutDuration) {
                lifeCycleDevice.start()
            }
            val duration = timeSource.now() - startTime
            messagingSystem.recordDuration("$name.start.duration", duration, name, mapOf("device_name" to name.toString()))
            messagingSystem.publish(messagingSystem.messageFactory.deviceStarted(name.toString()))
            logger.info { "Device '$name' started successfully in $duration." }
        } catch (e: TimeoutCancellationException) {
            messagingSystem.incrementCounter("$name.start.timeout", sourceDevice = name, tags = mapOf("device_name" to name.toString()))
            val failure = DeviceTimeoutException("Timeout ($startTimeoutDuration) starting device '$name'.", e)
            messagingSystem.publish(messagingSystem.messageFactory.deviceFailed(name.toString(), failure.toSerializableFailure()))
            messagingSystem.reportDeviceFailure(failure, name)
            deviceScope.cancel(CancellationException("Device '$name' start timed out, cancelling device scope.", failure)) // Cancel device scope
            throw failure
        } catch (e: DeviceException) {
            messagingSystem.incrementCounter("$name.start.error", sourceDevice = name, tags = mapOf("device_name" to name.toString(), "error_type" to (e::class.simpleName ?: "unknown")))
            messagingSystem.publish(messagingSystem.messageFactory.deviceFailed(name.toString(), e.toSerializableFailure()))
            messagingSystem.reportDeviceFailure(e, name)
            deviceScope.cancel(CancellationException("Device '$name' failed to start, cancelling device scope.", e)) // Cancel device scope
            throw e
        } catch (e: Exception) {
            messagingSystem.incrementCounter("$name.start.error", sourceDevice = name, tags = mapOf("device_name" to name.toString(), "error_type" to (e::class.simpleName ?: "unknown")))
            val failure = DeviceStartupException("Failed to start device '$name' due to an unexpected error.", e)
            messagingSystem.publish(messagingSystem.messageFactory.deviceFailed(name.toString(), failure.toSerializableFailure()))
            messagingSystem.reportDeviceFailure(failure, name)
            deviceScope.cancel(CancellationException("Device '$name' failed to start unexpectedly, cancelling device scope.", failure)) // Cancel device scope
            throw failure
        }
    }

    /**
     * Detaches a device from the system.
     */
    public suspend fun detachDevice(name: Name, waitStop: Boolean = false) {
        val deviceJob = registry.removeDevice(name)

        if (deviceJob != null) {
            messagingSystem.publish(messagingSystem.messageFactory.deviceRemoved(name.toString()))
            logger.info { "Device '$name' removed from registry (waitStop=$waitStop)." }

            val lifeCycleDevice = deviceJob.device as? WithLifeCycle
            if (lifeCycleDevice?.lifecycleState == LifecycleState.STARTED) {
                if (waitStop) {
                    logger.info { "Stopping device '$name' synchronously as part of detach."}
                    stopDeviceInternal(name, deviceJob, lifeCycleDevice)
                } else {
                    logger.info { "Stopping device '$name' asynchronously as part of detach."}
                    context.launch(CoroutineName("AsyncStop-$name")) {
                        stopDeviceInternal(name, deviceJob, lifeCycleDevice)
                    }
                }
            }
            messagingSystem.publish(messagingSystem.messageFactory.deviceDetached(name.toString()))
            logger.info { "Device '$name' fully detached." }
        } else {
            logger.warn { "Device '$name' not found for detachment." }
        }
    }

    /**
     * Internal utility to stop a device, handling timeouts and errors.
     */
    internal suspend fun stopDeviceInternal(name: Name, deviceJob: DeviceRegistry.DeviceJob, lifeCycleDevice: WithLifeCycle) {
        val stopTimeoutDuration = deviceJob.config.stopTimeout ?: hubConfig.defaultStopTimeout

        if (lifeCycleDevice.lifecycleState != LifecycleState.STARTED) {
            if (lifeCycleDevice.lifecycleState == LifecycleState.STOPPED) {
                messagingSystem.publish(messagingSystem.messageFactory.deviceStopped(name.toString()))
            }
            logger.info { "Device '$name' is not in STARTED state (current: ${lifeCycleDevice.lifecycleState}), skipping stop logic." }
            return
        }

        val startTime = timeSource.now()
        logger.info { "Attempting to stop device '$name'." }

        try {
            withTimeout(stopTimeoutDuration) {
                lifeCycleDevice.stop()
            }
            val duration = timeSource.now() - startTime
            messagingSystem.recordDuration("$name.stop.duration", duration, name, mapOf("device_name" to name.toString()))
            logger.info { "Device '$name' stopped successfully in $duration." }
        } catch (e: TimeoutCancellationException) {
            messagingSystem.incrementCounter("$name.stop.timeout", sourceDevice = name, tags = mapOf("device_name" to name.toString()))
            val failure = DeviceTimeoutException("Timeout ($stopTimeoutDuration) stopping device '$name'.", e)
            messagingSystem.reportDeviceFailure(failure, name)
            logger.warn { failure.message.toString() }
        } catch (e: DeviceException) {
            messagingSystem.incrementCounter("$name.stop.error", sourceDevice = name, tags = mapOf("device_name" to name.toString(), "error_type" to (e::class.simpleName ?: "unknown")))
            messagingSystem.reportDeviceFailure(e, name)
            logger.error(e) { "DeviceException while stopping '$name'." }
        } catch (e: Exception) {
            messagingSystem.incrementCounter("$name.stop.error", sourceDevice = name, tags = mapOf("device_name" to name.toString(), "error_type" to (e::class.simpleName ?: "unknown")))
            val failure = DeviceShutdownException("Failed to stop device '$name' due to an unexpected error.", e)
            messagingSystem.reportDeviceFailure(failure, name)
            logger.error(failure) { "Unexpected error while stopping '$name'." }
        } finally {
            messagingSystem.publish(messagingSystem.messageFactory.deviceStopped(name.toString()))
        }
    }

    /**
     * Stops a registered device.
     */
    public suspend fun stopDevice(name: Name) {
        val job = registry.getDeviceJob(name)
            ?: throw DeviceNotFoundException("Device '$name' not found for stop operation.")

        val lifeCycleDevice = job.device as? WithLifeCycle ?: run {
            logger.warn { "Device '$name' does not implement WithLifeCycle; cannot be explicitly stopped." }
            if (job.device.lifecycleState == LifecycleState.UNKNOWN || job.device.lifecycleState == LifecycleState.STARTED) {
                messagingSystem.publish(messagingSystem.messageFactory.deviceStopped(name.toString()))
            }
            return
        }
        stopDeviceInternal(name, job, lifeCycleDevice)
    }

    /**
     * Calculates restart delay based on policy and attempts.
     */
    internal fun calculateRestartDelay(policy: RestartPolicy, attempts: Int): Duration =
        policy.strategy.calculateDelay(policy.delayBetweenAttempts, attempts)
}