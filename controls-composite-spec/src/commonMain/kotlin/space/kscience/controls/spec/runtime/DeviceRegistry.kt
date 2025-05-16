package space.kscience.controls.spec.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import space.kscience.controls.api.Device
import space.kscience.controls.api.Message
import space.kscience.controls.api.id
import space.kscience.controls.spec.config.DeviceLifecycleConfig
import space.kscience.controls.spec.model.LifecycleMode
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * Manages a collection of devices, their configurations, and their message collector jobs.
 */
public class DeviceRegistry(private val context: Context) {

    /**
     * Represents a registered device along with its associated [CoroutineScope],
     * message collector [Job], lifecycle [config], and optional [meta]data.
     */
    public data class DeviceJob(
        val device: Device,
        val deviceScope: CoroutineScope,
        val collectorJob: Job,
        val config: DeviceLifecycleConfig,
        val meta: Meta? = null
    ) {
        val lifecycleMode: LifecycleMode get() = config.lifecycleMode
    }

    private val _deviceJobs = MutableStateFlow<Map<Name, DeviceJob>>(emptyMap())

    /**
     * A [StateFlow] emitting the current map of registered device jobs.
     * Suitable for observing changes to the set of managed devices.
     */
    public val deviceJobsFlow: StateFlow<Map<Name, DeviceJob>> = _deviceJobs.asStateFlow()

    /**
     * A snapshot map of registered devices. For observing changes, use [deviceJobsFlow].
     */
    public val devices: Map<Name, Device> get() = _deviceJobs.value.mapValues { it.value.device }

    /**
     * Registers a device with the registry.
     * A dedicated [CoroutineScope] and a message collector job are created for the device.
     *
     * @param name The [Name] to register the device under.
     * @param device The [Device] instance.
     * @param config The [DeviceLifecycleConfig] for this device.
     * @param meta Optional [Meta]data for the device.
     * @param messageHandler A suspend function to handle messages received from the device's message flow.
     * @return The created [DeviceJob].
     */
    public suspend fun registerDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        messageHandler: suspend (Message) -> Unit
    ): DeviceJob {
        val parentJobForDevice = coroutineContext[Job] ?: context.coroutineContext[Job]

        val actualDeviceScope = config.coroutineScope ?: CoroutineScope(
            SupervisorJob(parentJobForDevice) +
                    (config.dispatcher ?: context.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default) +
                    CoroutineName("DeviceScope-$name")
        )

        val collectorJob = actualDeviceScope.launch(CoroutineName("DeviceMsgCollector-$name")) {
            try {
                @Suppress("UNCHECKED_CAST")
                val flowToCollect: Flow<Message>? = device.messageFlow as? Flow<Message>

                flowToCollect?.catch { e ->
                    context.logger.error(e) { "Error in message flow from device '$name'." }
                }?.collect { msg ->
                    try {
                        val transformedMsg = msg.changeSource { baseId -> name.plus(baseId) }
                        messageHandler(transformedMsg)
                    } catch (e: Exception) {
                        context.logger.error(e) { "Error handling message from '$name': $msg." }
                    }
                }
            } catch (ex: CancellationException) {
                context.logger.debug { "Message collector for device '$name' cancelled." }
                throw ex
            } catch (ex: Exception) {
                context.logger.error(ex) { "Unexpected error collecting messages from '$name'." }
            }
        }

        val deviceJob = DeviceJob(device, actualDeviceScope, collectorJob, config, meta)
        _deviceJobs.update { it + (name to deviceJob) }
        context.logger.debug { "Device '$name' registered." }
        return deviceJob
    }

    /**
     * Retrieves the [DeviceJob] for a device by its [Name].
     */
    public fun getDeviceJob(name: Name): DeviceJob? = _deviceJobs.value[name]

    /**
     * Removes a device from the registry.
     * This cancels the device's dedicated [CoroutineScope] (which includes the collector job)
     * and waits for its completion.
     *
     * @param name The [Name] of the device to remove.
     * @return The removed [DeviceJob], or null if the device was not found.
     */
    public suspend fun removeDevice(name: Name): DeviceJob? {
        var removedJob: DeviceJob? = null
        _deviceJobs.update { currentJobs ->
            removedJob = currentJobs[name]
            if (removedJob != null) currentJobs - name else currentJobs
        }

        removedJob?.let {
            context.logger.debug { "Cancelling device scope for '$name' on removal." }
            it.deviceScope.cancel(CancellationException("Device '$name' removed from registry."))
            try {
                withTimeout(
                    it.config.stopTimeout ?: (DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT + 5.seconds)
                ) {
                    it.collectorJob.join()
                }
            } catch (e: TimeoutCancellationException) {
                context.logger.warn { "Timeout waiting for device '$name' scope to complete on removal."}
            }
            context.logger.debug { "Device '$name' removed from registry." }
        }
        return removedJob
    }

    /**
     * Updates a device in the registry. This involves removing the old [DeviceJob] (if one exists)
     * and adding the new one. The old device's scope is cancelled.
     *
     * @param name The [Name] of the device to update.
     * @param newJob The new [DeviceJob] to register.
     */
    public suspend fun updateDevice(name: Name, newJob: DeviceJob) {
        val oldJob = _deviceJobs.value[name]
        oldJob?.let {
            it.deviceScope.cancel(CancellationException("Device '$name' updated; cancelling old scope."))
            try {
                withTimeout(
                    it.config.stopTimeout ?: (DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT + 5.seconds)
                ) {
                    it.collectorJob.join()
                }
            } catch (e: TimeoutCancellationException) {
                context.logger.warn { "Timeout waiting for old job of device '$name' to complete on update."}
            }
        }
        _deviceJobs.update { it + (name to newJob) }
        context.logger.debug { "Device '$name' updated in registry." }
    }

    /**
     * Checks if a device with the given [Name] is registered.
     */
    public fun containsDevice(name: Name): Boolean = _deviceJobs.value.containsKey(name)

    /**
     * Gets the [Name]s of all currently registered devices.
     */
    public fun getDeviceNames(): Set<Name> = _deviceJobs.value.keys.toSet()

    /**
     * Clears the registry, removing all devices and cancelling their associated scopes.
     */
    public suspend fun clear() {
        val jobsToClear = _deviceJobs.value.values.toList()
        _deviceJobs.value = emptyMap()
        if (jobsToClear.isNotEmpty()) {
            context.logger.info { "Clearing device registry. Cancelling scopes for ${jobsToClear.size} devices." }
            supervisorScope {
                jobsToClear.forEach { deviceJob ->
                    launch {
                        deviceJob.deviceScope.cancel(CancellationException("Device registry cleared."))
                        try {
                            withTimeout(
                                deviceJob.config.stopTimeout
                                    ?: (DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT + 5.seconds)
                            ) {
                                deviceJob.collectorJob.join()
                            }
                        } catch (e: TimeoutCancellationException) {
                            context.logger.warn { "Timeout waiting for device '${deviceJob.device.id}' scope to complete during registry clear."}
                        }
                    }
                }
            }
        }
        context.logger.info { "Device registry cleared." }
    }
}