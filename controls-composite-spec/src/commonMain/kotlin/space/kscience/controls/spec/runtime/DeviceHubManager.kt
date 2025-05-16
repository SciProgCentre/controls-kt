package space.kscience.controls.spec.runtime

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import space.kscience.controls.api.*
import space.kscience.controls.spec.config.DeviceHubConfig
import space.kscience.controls.spec.config.DeviceLifecycleConfig
import space.kscience.controls.spec.config.MessageBusConfig
import space.kscience.controls.spec.infra.*
import space.kscience.controls.spec.model.*
import space.kscience.controls.spec.utils.ParsingUtils
import space.kscience.controls.spec.utils.TimeSource
import space.kscience.controls.spec.utils.deviceManagerConfig
import space.kscience.controls.spec.utils.timeSourceOrDefault
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * The main hub manager for device coordination.
 */
public class DeviceHubManager(
    override val context: Context,
    messageBusOverride: MessageBus? = null,
    private val timeSource: TimeSource = context.timeSourceOrDefault
) : AbstractPlugin(), ContextAware {

    override val tag: PluginTag get() = Companion.tag

    private val hubConfig: DeviceHubConfig = context.deviceManagerConfig

    private val resourceInfo: SystemResourceInfo by lazy { SystemResourceInfo(context) }

    public companion object : PluginFactory<DeviceHubManager> {
        override val tag: PluginTag = PluginTag("controls.device.hub", PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): DeviceHubManager {
            val hubConfigForBuild = DeviceHubConfig.fromMeta(meta)

            val sourceEndpoint = hubConfigForBuild.meta["sourceEndpoint"].string
                ?: MessageBusConfig.Factory.Defaults.DEFAULT_MAGIX_SOURCE_ENDPOINT

            val tracerImpl: MessageTracer? = hubConfigForBuild.meta["messageTracer.enabled"].boolean.let { enabled ->
                if (enabled == true) MessageTracer { message, _ ->
                    context.logger.debug { "[MessageTrace] Bus: ${message.messageType} from ${message.sourceDevice}" }
                } else null
            }

            val messageBus = MessageBusFactory.create(
                context,
                sourceEndpoint,
                hubConfigForBuild.messageBufferSize,
                tracer = tracerImpl
            )
            val timeSrc = context.timeSourceOrDefault

            return DeviceHubManager(context, messageBus, timeSrc)
        }
    }

    private val messageBus: MessageBus = messageBusOverride
        ?: MessageBusFactory.create(
            context,
            hubConfig.meta["sourceEndpoint"].string ?: MessageBusConfig.Factory.Defaults.DEFAULT_MAGIX_SOURCE_ENDPOINT,
            hubConfig.messageBufferSize,
            // TODO: Add tracer from hubConfig if defined
            tracer = hubConfig.meta["messageTracer.enabled"].boolean.let { enabled ->
                if (enabled == true) MessageTracer { message, _ ->
                    context.logger.debug { "[MessageTrace] Bus: ${message.messageType} from ${message.sourceDevice}" }
                } else null
            }
        )

    public val messagingSystem: MessagingSystem = MessagingSystem(messageBus, context.logger, timeSource)
    public val messages: Flow<Message> get() = messagingSystem.getAllMessages()
    public val deviceLogs: Flow<DeviceLogMessage> get() = messagingSystem.getDeviceLogMessages()
    public val systemLogs: Flow<SystemLogMessage> get() = messagingSystem.getSystemLogMessages()
    public val deviceStateEvents: Flow<DeviceStateMessage> get() = messagingSystem.getDeviceStateMessages()
    public val transactionEvents: Flow<TransactionMessage> get() = messagingSystem.getTransactionMessages()
    public val metricEvents: Flow<MetricMessage> get() = messagingSystem.getMetricMessages()

    private val metricsBackend: MetricsBackend = MessageBusMetricsBackend(messageBus)
    public val metricsPublisher: MetricsPublisher = MetricsPublisherImpl(
        metricsBackend,
        hubConfig.meta["metricsSourceDeviceName"]?.string?.let { Name.parse(it) } ?: MessageBusConfig.Factory.Defaults.METRICS_DEFAULT_SOURCE_DEVICE_NAME,
        context.logger,
        timeSource
    )
    public val transactionManager: TransactionManager = TransactionManagerImpl(messagingSystem, context.logger, timeSource)

    private val deviceRegistry = DeviceRegistry(context)
    internal val lifecycleManager = DeviceLifecycleManager(context, deviceRegistry, messagingSystem, timeSource, context.logger)
    private val circuitBreakerManager = CircuitBreakerManager(context, deviceRegistry, messagingSystem, timeSource, context.logger)
    private val restartManager = DeviceRestartManager(context, deviceRegistry, lifecycleManager, circuitBreakerManager, messagingSystem, timeSource, context.logger)

    private val exceptionHandler = CoroutineExceptionHandler { _, ex ->
        context.logger.error(ex) { "Unhandled exception in DeviceHubManager scope." }
        if (managerScope.isActive) {
            managerScope.launch {
                messagingSystem.logSystem(
                    "Unhandled exception in DeviceHubManager: ${ex::class.simpleName} - ${ex.message}",
                    "DeviceHubManagerError"
                )
            }
        } else {
            context.logger.error { "Manager scope inactive, cannot log unhandled DeviceHubManager exception." }
        }
    }

    private val parentJob = SupervisorJob(context.coroutineContext[Job])
    private val isActive = atomic(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val defaultDispatcher: CoroutineDispatcher = (context.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default)
        .limitedParallelism(resourceInfo.getConcurrencyLevel())

    private val managerScope: CoroutineScope = CoroutineScope(
        parentJob + defaultDispatcher + exceptionHandler + CoroutineName("DeviceHubManager")
    )

    private val cleanupJob: Job = managerScope.launch(CoroutineName("DeviceHubManager-Cleanup")) {
        while (this@DeviceHubManager.isActive.value && currentCoroutineContext().isActive) {
            try {
                timeSource.delay(hubConfig.resourceCleanupInterval)
                if (!this@DeviceHubManager.isActive.value) break
                val cbMaxIdleOpen = hubConfig.meta["circuitBreakerMaxIdleTimeOpen"]?.string?.let {
                    ParsingUtils.parseDurationOrNull(it)
                } ?: (hubConfig.resourceMaxIdleTime * 5)

                circuitBreakerManager.cleanup(hubConfig.resourceMaxIdleTime, cbMaxIdleOpen)
                restartManager.cleanup()
            } catch (_: CancellationException) {
                context.logger.info { "DeviceHubManager cleanup job cancelled." }
                break
            } catch (e: Exception) {
                context.logger.error(e) { "Error during DeviceHubManager resource cleanup." }
            }
        }
    }

    public val devices: Map<Name, Device> get() = deviceRegistry.devices

    public fun launchGlobal(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job = managerScope.launch(context = coroutineContext, block = block)

    public suspend fun recordDuration(name: String, duration: Duration, tags: Map<String, String> = emptyMap()): Unit =
        metricsPublisher.recordDuration(name, duration, tags)

    public suspend fun incrementCounter(name: String, amount: Double = 1.0, tags: Map<String, String> = emptyMap()): Unit =
        metricsPublisher.incrementCounter(name, amount, tags)

    public suspend fun recordGauge(name: String, value: Double, tags: Map<String, String> = emptyMap()): Unit =
        metricsPublisher.recordGauge(name, value, tags)

    public suspend fun recordDistribution(name: String, value: Double, tags: Map<String, String> = emptyMap()): Unit =
        metricsPublisher.recordDistribution(name, value, tags)

    public suspend fun publishMessage(message: Message): Unit = messagingSystem.publish(message)

    public suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        startMode: StartMode = StartMode.NONE
    ) {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is not active, cannot attach device '$name'.")
        }
        lifecycleManager.attachDevice(name, device, config, meta, startMode)
    }

    public suspend fun detachDevice(name: Name, waitStop: Boolean = false): Unit =
        lifecycleManager.detachDevice(name, waitStop)

    public suspend fun restartDevice(name: Name): Boolean {
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is not active, cannot restart device '$name'.")
        }
        return restartManager.restartDevice(name)
    }

    public suspend fun startDevicesBatch(deviceNames: List<Name>): Boolean =
        transactionManager.withTransaction { txContext ->
            val startedInThisBatch = mutableListOf<Name>()
            try {
                for (name in deviceNames) {
                    val deviceJob = deviceRegistry.getDeviceJob(name)
                        ?: throw DeviceNotFoundException("Device '$name' not found for batch start.")
                    if ((deviceJob.device as? WithLifeCycle)?.lifecycleState == LifecycleState.STARTED) {
                        logger.info { "Device '$name' in batch start is already started, skipping." }
                        continue
                    }
                    lifecycleManager.startDevice(name, deviceJob.config, deviceJob.device)
                    startedInThisBatch.add(name)
                    txContext.recordAction(object : ReversibleAction {
                        override val id: String = "start_device_batch_$name"
                        override suspend fun reverse() {
                            try {
                                logger.info { "Rolling back batch start: Stopping device '$name'." }
                                lifecycleManager.stopDevice(name)
                            } catch (e: Exception) {
                                logger.warn { "Failed to reverse batch start (stop '$name') for TX ${txContext.id}." }
                            }
                        }
                    })
                }
                messagingSystem.logSystem("Batch start successful for devices: $startedInThisBatch.", "DeviceHubManager")
                true
            } catch (ex: Exception) {
                logger.error(ex) { "Failed to start devices batch ($deviceNames), initiating rollback." }
                messagingSystem.logSystem("Batch start failed for devices ($deviceNames): ${ex.message}. Rolling back.", "DeviceHubManager")
                throw ex
            }
        }

    public suspend fun stopDevicesBatch(deviceNames: List<Name>): Boolean =
        transactionManager.withTransaction { txContext ->
            val stoppedInThisBatch = mutableListOf<Name>()
            try {
                for (name in deviceNames) {
                    val deviceJob = deviceRegistry.getDeviceJob(name)
                        ?: throw DeviceNotFoundException("Device '$name' not found for batch stop.")
                    if ((deviceJob.device as? WithLifeCycle)?.lifecycleState == LifecycleState.STARTED) {
                        lifecycleManager.stopDevice(name)
                        stoppedInThisBatch.add(name)
                        txContext.recordAction(object : ReversibleAction {
                            override val id: String = "stop_device_batch_$name"
                            override suspend fun reverse() {
                                try {
                                    logger.info { "Rolling back batch stop: Starting device '$name'." }
                                    lifecycleManager.startDevice(name, deviceJob.config, deviceJob.device)
                                } catch (e: Exception) {
                                    logger.warn { "Failed to reverse batch stop (start '$name') for TX ${txContext.id}." }
                                }
                            }
                        })
                    } else {
                        logger.info { "Device '$name' in batch stop is already stopped or not applicable, skipping." }
                    }
                }
                messagingSystem.logSystem("Batch stop successful for devices: $stoppedInThisBatch.", "DeviceHubManager")
                true
            } catch (ex: Exception) {
                logger.error(ex) { "Failed to stop devices batch ($deviceNames), initiating rollback." }
                messagingSystem.logSystem("Batch stop failed for devices ($deviceNames): ${ex.message}. Rolling back.", "DeviceHubManager")
                throw ex
            }
        }

    public suspend fun hotSwapDevice(
        name: Name,
        newDevice: Device,
        newConfig: DeviceLifecycleConfig,
        newMeta: Meta? = null
    ): Unit = transactionManager.withTransaction { txContext ->
        if (!isActive.value) {
            throw DeviceConfigurationException("DeviceHubManager is not active, cannot hot swap device '$name'.")
        }
        val oldJob = deviceRegistry.getDeviceJob(name)
        var oldDeviceWasStarted = false

        if (oldJob != null) {
            oldDeviceWasStarted = (oldJob.device as? WithLifeCycle)?.lifecycleState == LifecycleState.STARTED
            txContext.recordAction(object : ReversibleAction {
                override val id: String = "hotSwap_revert_$name"
                override suspend fun reverse() {
                    logger.info { "Reverting hot swap for '$name' to old device configuration." }
                    val currentJobForNew = deviceRegistry.getDeviceJob(name)
                    if (currentJobForNew != null && currentJobForNew.device === newDevice) {
                        if ((currentJobForNew.device as? WithLifeCycle)?.lifecycleState == LifecycleState.STARTED) {
                            try { lifecycleManager.stopDevice(name) } catch (e: Exception) {
                                logger.warn { "Error stopping new device '$name' during hotswap revert." }
                            }
                        }
                        deviceRegistry.removeDevice(name)
                    }
                    deviceRegistry.registerDevice(name, oldJob.device, oldJob.config, oldJob.meta) { msg ->
                        messagingSystem.publish(msg)
                    }
                    if (oldDeviceWasStarted && oldJob.config.lifecycleMode != LifecycleMode.INDEPENDENT) {
                        try {
                            lifecycleManager.startDevice(name, oldJob.config, oldJob.device)
                        } catch (e: Exception) {
                            logger.warn { "Error restarting old device '$name' during hotswap revert." }
                        }
                    }
                    logger.info { "Hot swap revert for '$name' completed." }
                }
            })
            if (oldDeviceWasStarted) {
                lifecycleManager.stopDevice(name)
            }
            deviceRegistry.removeDevice(name)
            logger.info { "Old device '$name' stopped and removed for hot swap." }
        }

        val startModeForNew = if (newConfig.lifecycleMode != LifecycleMode.INDEPENDENT && (oldJob == null || oldDeviceWasStarted)) {
            StartMode.SYNC
        } else {
            StartMode.NONE
        }
        lifecycleManager.attachDevice(name, newDevice, newConfig, newMeta, startModeForNew)
        messagingSystem.logSystem("Device '$name' successfully hot-swapped.", "DeviceHubManager")
    }

    public suspend fun publishLog(deviceName: Name? = null, message: String) {
        if (deviceName != null) {
            messagingSystem.logDevice(message, deviceName)
        } else {
            messagingSystem.logSystem(message, "DeviceHubManager")
        }
    }

    public suspend fun shutdown() {
        if (!isActive.compareAndSet(expect = true, update = false)) {
            context.logger.info { "DeviceHubManager shutdown already in progress or completed." }
            return
        }
        context.logger.info { "Starting DeviceHubManager shutdown..." }
        cleanupJob.cancelAndJoin()
        context.logger.info { "Cleanup job cancelled." }

        val deviceNames = deviceRegistry.getDeviceNames().toList()
        context.logger.info { "Detaching ${deviceNames.size} devices: $deviceNames." }
        supervisorScope {
            val detachJobs = deviceNames.map { name ->
                launch(CoroutineName("Shutdown-Detach-$name")) {
                    try {
                        val detachTimeout = hubConfig.defaultStopTimeout + 5.seconds
                        withTimeout(detachTimeout) { detachDevice(name, true) }
                    } catch (_: TimeoutCancellationException) {
                        context.logger.error { "Timed out detaching device '$name' during shutdown." }
                    } catch (e: Exception) {
                        context.logger.error(e) { "Error detaching device '$name' during shutdown." }
                    }
                }
            }
            try {
                val overallDetachTimeout = (hubConfig.defaultStopTimeout * deviceNames.size.coerceAtLeast(1)) + (10.seconds * deviceNames.size.coerceAtLeast(1))
                withTimeout(overallDetachTimeout) { detachJobs.joinAll() }
            } catch (_: TimeoutCancellationException) {
                context.logger.warn { "Timed out waiting for all devices to complete detachment." }
            }
        }
        context.logger.info { "All device detachment attempts completed." }
        deviceRegistry.clear()

        try { messageBus.close(); context.logger.info { "MessageBus closed." } }
        catch (e: Exception) { context.logger.error(e) { "Error closing message bus." } }
        try { metricsPublisher.close(); context.logger.info { "MetricsPublisher closed." } }
        catch (e: Exception) { context.logger.error(e) { "Error closing metrics publisher." } }

        parentJob.cancelAndJoin()
        context.logger.info { "DeviceHubManager shutdown completed." }
    }

    public fun deviceExists(name: Name): Boolean = deviceRegistry.containsDevice(name)
    public fun getAllDeviceNames(): Set<Name> = deviceRegistry.getDeviceNames()

    public suspend fun getCircuitBreakerStatus(deviceName: Name): Map<String, Any>? =
        circuitBreakerManager.getCircuitBreakerStatus(deviceName)

    public suspend fun resetRestartAttempts(deviceName: Name): Unit =
        restartManager.resetRestartAttempts(deviceName)

    public suspend fun getRestartAttemptCount(deviceName: Name): Int =
        restartManager.getRestartAttemptCount(deviceName)
}