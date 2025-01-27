package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.*
import kotlin.math.pow
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * Defines how child device lifecycle should be managed.
 */
public enum class LifecycleMode {
    /**
     * The device starts and stops together with the parent.
     */
    LINKED,

    /**
     * The device is started and stopped independently from the parent.
     */
    INDEPENDENT,

    /**
     * The device is created but starts only upon an explicit request.
     */
    LAZY
}

/**
 * An interface for external configuration sources.
 */
public interface ExternalConfigurationProvider {
    /**
     * Load custom configuration from an external system. Return a [Meta] if any.
     */
    public suspend fun loadExternalConfig(name: Name): Meta?
}

/**
 * A basic interface for device health checking.
 */
public fun interface HealthChecker {
    /**
     * Return true if the given [device] is healthy, false otherwise.
     */
    public suspend fun isHealthy(device: Device): Boolean
}

/**
 * Defines child device error handling policy, extended with a CUSTOM option.
 */
public enum class ChildDeviceErrorHandler {
    IGNORE,
    RESTART,
    STOP_PARENT,
    PROPAGATE,
    /**
     * A custom user-defined strategy can be handled in [AbstractDeviceHubManager.onCustomError].
     */
    CUSTOM,
}

public data class RestartPolicy(
    val maxAttempts: Int = Int.MAX_VALUE,
    val delayBetweenAttempts: Duration = Duration.ZERO,
    val resetOnSuccess: Boolean = true,
    val strategy: RestartStrategy = RestartStrategy.LINEAR,
)

public enum class RestartStrategy {
    LINEAR,
    EXPONENTIAL_BACKOFF,
    CUSTOM,
}

/**
 * Represents different possible device state changes or events.
 */
public sealed class DeviceStateEvent {
    public abstract val deviceName: Name

    public data class DeviceAdded(override val deviceName: Name) : DeviceStateEvent()
    public data class DeviceStarted(override val deviceName: Name) : DeviceStateEvent()
    public data class DeviceStopped(override val deviceName: Name) : DeviceStateEvent()
    public data class DeviceRemoved(override val deviceName: Name) : DeviceStateEvent()
    public data class DeviceFailed(override val deviceName: Name, val error: Throwable) : DeviceStateEvent()
    public data class DeviceDetached(override val deviceName: Name) : DeviceStateEvent()
}

/**
 * Holds lifecycle-related configuration for devices, with optional fields.
 */
public data class DeviceLifecycleConfig(
    val lifecycleMode: LifecycleMode = LifecycleMode.LINKED,
    val messageBuffer: Int = 1000,
    val startDelay: Duration = Duration.ZERO,
    val startTimeout: Duration? = null,
    val stopTimeout: Duration? = null,
    val coroutineScope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher? = null,
    val onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART,
    val healthChecker: HealthChecker? = null,
    val restartPolicy: RestartPolicy? = null,
) {
    init {
        require(messageBuffer > 0) { "Message buffer size must be positive." }
        startTimeout?.let { require(it.isPositive()) { "Start timeout must be positive." } }
        stopTimeout?.let { require(it.isPositive()) { "Stop timeout must be positive." } }
    }
}

/**
 * An interface to attach external config to a [DeviceLifecycleConfigBuilder].
 */
public fun interface ExternalConfigApplier {
    public suspend fun applyConfig(builder: DeviceLifecycleConfigBuilder, deviceName: Name)
}

/**
 * Builder for [DeviceLifecycleConfig].
 */
public class DeviceLifecycleConfigBuilder {
    public var lifecycleMode: LifecycleMode = LifecycleMode.LINKED
    public var messageBuffer: Int = 1000
    public var startDelay: Duration = Duration.ZERO
    public var startTimeout: Duration? = null
    public var stopTimeout: Duration? = null
    public var coroutineScope: CoroutineScope? = null
    public var dispatcher: CoroutineDispatcher? = null
    public var onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART
    public var healthChecker: HealthChecker? = null
    public var restartPolicy: RestartPolicy? = null

    public suspend fun applyExternalConfig(deviceName: Name, externalApplier: ExternalConfigApplier) {
        externalApplier.applyConfig(this, deviceName)
    }

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

public fun DeviceLifecycleConfigBuilder.linked() { lifecycleMode = LifecycleMode.LINKED }
public fun DeviceLifecycleConfigBuilder.independent() { lifecycleMode = LifecycleMode.INDEPENDENT }
public fun DeviceLifecycleConfigBuilder.lazy() { lifecycleMode = LifecycleMode.LAZY }
public fun DeviceLifecycleConfigBuilder.restartOnError() { onError = ChildDeviceErrorHandler.RESTART }
public fun DeviceLifecycleConfigBuilder.propagateError() { onError = ChildDeviceErrorHandler.PROPAGATE }
public fun DeviceLifecycleConfigBuilder.withCustomTimeout(timeout: Duration) {
    startTimeout = timeout
    stopTimeout = timeout
}

/**
 * Provides access to a registry of specifications.
 */
public interface ComponentRegistry : ContextAware {
    public fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>?
}

/**
 * A default plugin implementation of [ComponentRegistry].
 */
public class ComponentRegistryManager : AbstractPlugin(), ComponentRegistry {
    private val specs = mutableMapOf<Name, CompositeControlComponentSpec<*>>()

    override val tag: PluginTag = Companion.tag

    @Suppress("UNCHECKED_CAST")
    override fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>? {
        return try {
            specs[name] as? CompositeControlComponentSpec<D>
        } catch (e: ClassCastException) {
            logger.error(e) { "Failed to get spec $name" }
            null
        }
    }

    public fun registerSpec(spec: CompositeControlComponentSpec<*>, name: Name) {
        specs[name] = spec
    }

    public companion object : PluginFactory<ComponentRegistryManager> {
        override val tag: PluginTag = PluginTag("controls.spechub", group = PluginTag.DATAFORGE_GROUP)
        override fun build(context: Context, meta: Meta): ComponentRegistryManager = ComponentRegistryManager()
    }
}

public val Context.componentRegistry: ComponentRegistry?
    get() = plugins[ComponentRegistryManager]

public fun ContextBuilder.withSpecHub() {
    plugin(ComponentRegistryManager)
}

/**
 * Aggregates configuration for a child device.
 */
public interface ChildComponentConfig<CD : ConfigurableCompositeControlComponent<CD>> {
    public val spec: CompositeControlComponentSpec<CD>
    public val config: DeviceLifecycleConfig
    public val meta: Meta?
    public val name: Name
}

/**
 * Base specification for a composite device.
 */
public interface CompositeDeviceSpec<D : ConfigurableCompositeControlComponent<D>> {
    public val properties: Map<String, DevicePropertySpec<D, *>>
    public val actions: Map<String, DeviceActionSpec<D, *, *>>
    public val childSpecs: Map<String, ChildComponentConfig<*>>

    public suspend fun D.onOpen()
    public suspend fun D.onClose()
    public fun validate(device: D)

    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    public fun createPropertyDescriptorInternal(
        propertyName: String,
        converter: MetaConverter<*>,
        mutable: Boolean,
        property: KProperty<*>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit
    ): PropertyDescriptor

    public fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        property: KProperty<*>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit
    ): ActionDescriptor

    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>>

    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>>

    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(I) -> O,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>>
}

/**
 * Default implementation of [CompositeDeviceSpec].
 */
@OptIn(InternalDeviceAPI::class)
public open class CompositeControlComponentSpec<D : ConfigurableCompositeControlComponent<D>>(
    public val registry: ComponentRegistry? = null
) : CompositeDeviceSpec<D> {
    private val propertyMap = hashMapOf<String, DevicePropertySpec<D, *>>(
        DeviceMetaPropertySpec.name to DeviceMetaPropertySpec
    )
    private val actionMap = hashMapOf<String, DeviceActionSpec<D, *, *>>()
    private val childSpecMap = mutableMapOf<String, ChildComponentConfig<*>>()

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = propertyMap

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = actionMap

    override val childSpecs: Map<String, ChildComponentConfig<*>>
        get() = childSpecMap

    override suspend fun D.onOpen() {}
    override suspend fun D.onClose() {}

    override fun validate(device: D) {
        properties.values.forEach { prop ->
            check(prop.descriptor in device.propertyDescriptors) {
                "Property ${prop.descriptor.name} not registered in ${device.id}"
            }
        }
        actions.values.forEach { act ->
            check(act.descriptor in device.actionDescriptors) {
                "Action ${act.descriptor.name} not registered in ${device.id}"
            }
        }
    }

    override fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P {
        propertyMap[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    override fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
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
            fromSpec(property)
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
            fromSpec(property)
            descriptorBuilder()
        }
    }

    override fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, prop ->
            val propertyName = name ?: prop.name
            val descriptor = createPropertyDescriptorInternal(
                propertyName, converter, mutable = false, property = prop, descriptorBuilder = descriptorBuilder
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
        PropertyDelegateProvider { _, prop ->
            val propertyName = name ?: prop.name
            val descriptor = createPropertyDescriptorInternal(
                propertyName, converter, mutable = true, property = prop, descriptorBuilder = descriptorBuilder
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

    public fun <CDS : CompositeControlComponentSpec<CD>, CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        fallbackSpec: CDS,
        specKeyInRegistry: Name? = null,
        childDeviceName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {},
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { _, property ->
            ReadOnlyProperty { _, _ ->
                val registryKey = specKeyInRegistry ?: property.name.asName()
                val childName = childDeviceName ?: property.name.asName()
                val config = DeviceLifecycleConfigBuilder().apply(configBuilder).build()
                val meta = metaBuilder?.let { Meta(it) }
                val fromRegistry: CompositeControlComponentSpec<CD>? =
                    registry?.getSpec<CD>(registryKey)

                val foundSpec: CompositeControlComponentSpec<CD> = fromRegistry ?: fallbackSpec

                val mapKey = childName.toString()
                check(childSpecMap[mapKey] == null) {
                    "Child spec with name '$mapKey' is already registered in $this."
                }

                val childConfig = object : ChildComponentConfig<CD> {
                    override val spec: CompositeControlComponentSpec<CD> = foundSpec
                    override val config: DeviceLifecycleConfig = config
                    override val meta: Meta? = meta
                    override val name: Name = childName
                }
                childSpecMap[mapKey] = childConfig
                childConfig.spec
            }
        }

    override fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit,
        name: String?,
        execute: suspend D.(I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _, prop ->
            val actionName = name ?: prop.name
            val descriptor = createActionDescriptor(actionName, inputConverter, outputConverter, prop, descriptorBuilder)
            val devAction = registerAction(object : DeviceActionSpec<D, I, O> {
                override val descriptor: ActionDescriptor = descriptor
                override val inputConverter: MetaConverter<I> = inputConverter
                override val outputConverter: MetaConverter<O> = outputConverter
                override suspend fun execute(device: D, input: I): O =
                    withContext(device.coroutineContext) { device.execute(input) }
            })
            ReadOnlyProperty { _, _ -> devAction }
        }
}

/**
 * Defines an action with Unit input and Unit output.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.unitAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.() -> Unit,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    action(MetaConverter.unit, MetaConverter.unit, descriptorBuilder, name) {
        execute()
    }

/**
 * Defines an action with Meta input and Meta output.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(Meta) -> Meta,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    action(MetaConverter.meta, MetaConverter.meta, descriptorBuilder, name) {
        execute(it)
    }

/**
 * An extended device manager that supports advanced lifecycle, errors, transactions, etc.
 */
public abstract class AbstractDeviceHubManager(
    public val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * An optional parentJob to differentiate the parent's context from the child's context.
     */
    protected val parentJob: Job = SupervisorJob()

    private val childLock = Mutex()

    internal val childrenJobs: MutableMap<Name, ChildJob> = mutableMapOf()

    public val devices: Map<Name, Device>
        get() = childrenJobs.mapValues { it.value.device }

    public abstract val messageBus: MutableSharedFlow<DeviceMessage>
    /**
     * Additional stream for system events/logs.
     */
    public abstract val systemBus: MutableSharedFlow<SystemLogMessage>

    public abstract val deviceChanges: MutableSharedFlow<DeviceStateEvent>

    internal val restartAttemptsMap: MutableMap<Name, Int> = mutableMapOf()

    /**
     * A data class describing a child device, its job, config, and dedicated messageBus.
     */
    internal data class ChildJob(
        val device: Device,
        val job: Job,
        val config: DeviceLifecycleConfig,
        val messageBus: MutableSharedFlow<DeviceMessage>,
        val systemBus: MutableSharedFlow<SystemLogMessage>,
        val meta: Meta? = null,
        /**
         * If true, we keep the old messageBus on hotSwap.
         */
        val reuseBus: Boolean = false
    ) {
        val lifecycleMode: LifecycleMode get() = config.lifecycleMode
    }

    /**
     * Called when a child device error occurs.
     */
    protected open suspend fun onChildErrorCaught(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Error in child device $childName with policy ${config.onError}" }
    }

    /**
     * Called when STOP_PARENT policy is triggered.
     * We cancel the [parentJob] instead of the current context to avoid self-cancellation in child coroutines.
     */
    protected open suspend fun onParentStopRequested(ex: Throwable, childName: Name) {
        context.logger.error(ex) { "Stopping parent due to error in child $childName" }
        parentJob.cancelAndJoin()
    }

    /**
     * Called when a CUSTOM error policy is triggered.
     */
    protected open suspend fun onCustomError(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Custom error strategy for device $childName: override onCustomError." }
    }

    /**
     * Called if the device's start operation times out.
     */
    protected open suspend fun onStartTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.error { "Timeout while starting $deviceName." }
        throw RuntimeException("Timeout on start for $deviceName")
    }

    /**
     * Called if the device's stop operation times out.
     */
    internal open suspend fun onStopTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.warn { "Timeout while stopping $deviceName. Consider overriding onStopTimeout." }
    }

    /**
     * Perform a health check if configured.
     */
    internal open suspend fun checkHealth(child: ChildJob) {
        val hc = child.config.healthChecker ?: return
        if (!hc.isHealthy(child.device)) {
            val ex = RuntimeException("Health check failed for device ${child.device.id}")
            deviceChanges.emit(DeviceStateEvent.DeviceFailed(child.device.id.parseAsName(), ex))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun launchChild(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta?,
        /**
         * If true, we reuse the old messageBus from previous device on hotSwap.
         * Otherwise, we create a new bus.
         */
        reuseBus: MutableSharedFlow<DeviceMessage>? = null
    ): ChildJob {
        val childMessageBus = reuseBus ?: MutableSharedFlow(
            replay = config.messageBuffer,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val childScope = config.coroutineScope ?: CoroutineScope(parentJob + dispatcher)

        val childJob = childScope.launch(CoroutineName("Child device $name")) {
            try {
                // Attempt to auto-start if not independent.
                if (config.lifecycleMode != LifecycleMode.INDEPENDENT) {
                    if (config.lifecycleMode == LifecycleMode.LINKED ||
                        device.lifecycleState == LifecycleState.STARTING ||
                        device.lifecycleState == LifecycleState.INITIAL
                    ) {
                        delay(config.startDelay)
                        val started = withTimeoutOrNull(config.startTimeout ?: Duration.INFINITE) {
                            device.start()
                        }
                        if (started == null) {
                            onStartTimeout(name, config)
                        } else {
                            deviceChanges.emit(DeviceStateEvent.DeviceStarted(name))
                            if (config.restartPolicy?.resetOnSuccess == true) {
                                restartAttemptsMap[name] = 0
                            }
                            checkHealth(
                                ChildJob(device, this.coroutineContext[Job]!!, config, childMessageBus, systemBus, meta, reuseBus != null)
                            )
                        }
                    }
                }
                // Collect all device messages
                device.messageFlow.collect { msg ->
                    val wrapped = msg.changeSource { name.plus(it) }
                    childMessageBus.emit(wrapped)
                    messageBus.emit(wrapped)
                }
            } catch (ex: Exception) {
                onChildErrorCaught(ex, name, config)
                deviceChanges.emit(DeviceStateEvent.DeviceFailed(name, ex))
                messageBus.emit(DeviceMessage.error(ex, name))

                when (config.onError) {
                    ChildDeviceErrorHandler.IGNORE -> {}
                    ChildDeviceErrorHandler.RESTART -> {
                        val policy = config.restartPolicy
                        if (policy != null) {
                            val attempts = (restartAttemptsMap[name] ?: 0) + 1
                            restartAttemptsMap[name] = attempts

                            if (attempts > policy.maxAttempts) {
                                context.logger.warn { "Max restart attempts exceeded for $name." }
                            } else {
                                val delayDuration = calculateDelay(policy, attempts)
                                if (delayDuration > Duration.ZERO) {
                                    delay(delayDuration)
                                }
                                CoroutineScope(parentJob + dispatcher).launch {
                                    restartDevice(name)
                                }
                            }
                        } else {
                            // schedule restart
                            CoroutineScope(parentJob + dispatcher).launch {
                                restartDevice(name)
                            }
                        }
                    }
                    ChildDeviceErrorHandler.STOP_PARENT -> onParentStopRequested(ex, name)
                    ChildDeviceErrorHandler.PROPAGATE -> throw ex
                    ChildDeviceErrorHandler.CUSTOM -> onCustomError(ex, name, config)
                }
            } finally {
                // If ended, device is considered stopped.
                if (!isActive) {
                    removeJobFromRegistry(name, device, childMessageBus)
                    deviceChanges.emit(DeviceStateEvent.DeviceStopped(name))
                }
            }
        }
        return ChildJob(device, childJob, config, childMessageBus, systemBus, meta, reuseBus != null)
    }

    private fun calculateDelay(policy: RestartPolicy, attempts: Int): Duration {
        return when (policy.strategy) {
            RestartStrategy.LINEAR -> {
                policy.delayBetweenAttempts
            }
            RestartStrategy.EXPONENTIAL_BACKOFF -> {
                policy.delayBetweenAttempts * 2.0.pow((attempts - 1).toDouble())
            }
            RestartStrategy.CUSTOM -> {
                Duration.ZERO
            }
        }
    }

    /**
     * Removes the child device from registry.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun removeJobFromRegistry(
        name: Name,
        device: Device,
        bus: MutableSharedFlow<DeviceMessage>
    ) {
        childLock.withLock {
            val current = childrenJobs[name]
            if (current?.device == device) {
                childrenJobs.remove(name)
                restartAttemptsMap.remove(name)
            }
        }
        bus.resetReplayCache()
        deviceChanges.emit(DeviceStateEvent.DeviceDetached(name))
        systemBus.emit(SystemLogMessage("Device $name physically removed.", sourceDevice = name))
        if (device is ConfigurableCompositeControlComponent<*>) {
            device.onChildStop()
        }
    }

    /**
     * Adds a device asynchronously (does not wait for full start).
     */
    public suspend fun addDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null
    ) {
        childLock.withLock {
            val existing = childrenJobs[name]
            if (existing != null && existing.device != device) {
                removeDeviceUnlocked(name, waitStop = false)
            }
            val newChild = launchChild(name, device, config, meta)
            childrenJobs[name] = newChild
        }
        deviceChanges.emit(DeviceStateEvent.DeviceAdded(name))
        systemBus.emit(SystemLogMessage("Device $name added", sourceDevice = name))
    }

    /**
     * Adds a device and joins the child job if it completes.
     */
    public suspend fun addDeviceSync(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null
    ) {
        addDevice(name, device, config, meta)
        childLock.withLock {
            childrenJobs[name]?.job?.join()
        }
    }

    /**
     * Removes a device asynchronously (not waiting for full stop).
     */
    public suspend fun removeDevice(name: Name) {
        childLock.withLock {
            removeDeviceUnlocked(name, waitStop = false)
        }
    }

    /**
     * Restarts a device, preserving the same config & meta.
     */
    public suspend fun restartDevice(name: Name) {
        childLock.withLock {
            val old = childrenJobs[name] ?: return
            removeDeviceUnlocked(name, waitStop = true)
            val newChild = launchChild(name, old.device, old.config, old.meta, if (old.reuseBus) old.messageBus else null)
            childrenJobs[name] = newChild
            systemBus.emit(SystemLogMessage("Device $name restarted", sourceDevice = name))
        }
    }

    /**
     * Changes the child's lifecycle mode.
     */
    public suspend fun changeLifecycleMode(name: Name, newMode: LifecycleMode) {
        childLock.withLock {
            val old = childrenJobs[name] ?: error("Device $name not found")
            val newConfig = old.config.copy(lifecycleMode = newMode)
            removeDeviceUnlocked(name, waitStop = true)
            val newChild = launchChild(name, old.device, newConfig, old.meta, if (old.reuseBus) old.messageBus else null)
            childrenJobs[name] = newChild
            systemBus.emit(SystemLogMessage("Device $name lifecycle changed to $newMode", sourceDevice = name))
        }
    }

    /**
     * A "hot swap" approach, optionally reusing the old bus to keep subscribers.
     */
    public suspend fun hotSwapDevice(
        name: Name,
        newDevice: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        reuseMessageBus: Boolean = false
    ) {
        childLock.withLock {
            val old = childrenJobs[name]
            val oldBus = if (reuseMessageBus) old?.messageBus else null
            removeDeviceUnlocked(name, waitStop = true)
            val newChild = launchChild(name, newDevice, config, meta, oldBus)
            childrenJobs[name] = newChild
            systemBus.emit(SystemLogMessage("Device $name hot-swapped", sourceDevice = name))
        }
    }

    /**
     * Remove device, either waiting or not. Will attempt to stop within [DeviceLifecycleConfig.stopTimeout].
     */
    private suspend fun removeDeviceUnlocked(name: Name, waitStop: Boolean) {
        val child = childrenJobs[name] ?: return
        childrenJobs.remove(name)
        restartAttemptsMap.remove(name)
        val timeout = child.config.stopTimeout ?: Duration.INFINITE
        if (waitStop) {
            child.job.cancelAndJoin()
            val stopped = withTimeoutOrNull(timeout) {
                child.device.stop()
            }
            if (stopped == null) onStopTimeout(name, child.config)
            deviceChanges.emit(DeviceStateEvent.DeviceRemoved(name))
            systemBus.emit(SystemLogMessage("Device $name removed (waitStop=true)", sourceDevice = name))
        } else {
            // do not wait, just launch
            CoroutineScope(parentJob + dispatcher).launch {
                try{
                    val stopped = withTimeoutOrNull(timeout) {
                        child.job.cancelAndJoin()
                        child.device.stop()
                    }
                    if (stopped == null) onStopTimeout(name, child.config)
                } catch (ex: Throwable) {
                    context.logger.error(ex) { "Exception while stopping device $name (async remove)" }
                }
            }
            deviceChanges.emit(DeviceStateEvent.DeviceRemoved(name))
            systemBus.emit(SystemLogMessage("Device $name removed (async)", sourceDevice = name))
        }
    }

    public fun getChildMessageBus(name: Name): MutableSharedFlow<DeviceMessage>? = childrenJobs[name]?.messageBus

    internal open fun onChildStop() {}

    /**
     * Starts multiple devices in a transactional manner.
     * If any fails, we stop all that started successfully, logging any rollback errors.
     */
    public suspend fun startDevicesBatch(deviceNames: List<Name>): Boolean {
        val startedSuccessfully = mutableListOf<Name>()
        try {
            for (dn in deviceNames) {
                val job = childrenJobs[dn] ?: continue
                if (job.device.lifecycleState == LifecycleState.INITIAL) {
                    job.device.start()
                    deviceChanges.emit(DeviceStateEvent.DeviceStarted(dn))
                    startedSuccessfully += dn
                }
            }
            return true
        } catch (_: Exception) {
            // rollback
            for (dn in startedSuccessfully) {
                val job = childrenJobs[dn] ?: continue
                try {
                    job.device.stop()
                    deviceChanges.emit(DeviceStateEvent.DeviceStopped(dn))
                } catch (rollbackEx: Exception) {
                    context.logger.error(rollbackEx) { "Failed to rollback stop for device $dn" }
                }
            }
            return false
        }
    }

    /**
     * Stops multiple devices in a transactional manner.
     * If any fails to stop, we attempt to restart those already stopped, logging any errors.
     */
    public suspend fun stopDevicesBatch(deviceNames: List<Name>): Boolean {
        val stoppedSuccessfully = mutableListOf<Name>()
        try {
            for (dn in deviceNames) {
                val job = childrenJobs[dn] ?: continue
                if (job.device.lifecycleState == LifecycleState.STARTED) {
                    job.device.stop()
                    deviceChanges.emit(DeviceStateEvent.DeviceStopped(dn))
                    stoppedSuccessfully += dn
                }
            }
            return true
        } catch (_: Exception) {
            for (dn in stoppedSuccessfully) {
                val job = childrenJobs[dn] ?: continue
                try {
                    job.device.start()
                    deviceChanges.emit(DeviceStateEvent.DeviceStarted(dn))
                } catch (rollbackEx: Exception) {
                    context.logger.error(rollbackEx) { "Failed to rollback start for device $dn" }
                }
            }
            return false
        }
    }

    /**
     * A placeholder method to install distributed transport.
     */
    public open fun installDistributedTransport() {
        context.logger.info { "installDistributedTransport: implement broker here." }
    }
}

/**
 * A default implementation of the device hub manager with extended event types.
 * We store a [parentJob] for controlling all child coroutines from the parent side.
 */
private class DeviceHubManagerImpl(context: Context, dispatcher: CoroutineDispatcher) : AbstractDeviceHubManager(context, dispatcher) {
    override val messageBus: MutableSharedFlow<DeviceMessage> = MutableSharedFlow(
        replay = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val systemBus: MutableSharedFlow<SystemLogMessage> = MutableSharedFlow(
        replay = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val deviceChanges: MutableSharedFlow<DeviceStateEvent> = MutableSharedFlow(replay = 1)
}

/**
 * A device that can host child devices.
 */
public interface CompositeControlComponent : Device {
    public val messageBus: SharedFlow<DeviceMessage>
    public val devices: Map<Name, Device>
}

/**
 * A base device created from [spec] with an optional [registry].
 */
public open class ConfigurableCompositeControlComponent<D : ConfigurableCompositeControlComponent<D>>(
    public open val spec: CompositeControlComponentSpec<D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
    config: DeviceLifecycleConfig = DeviceLifecycleConfig(),
    registry: ComponentRegistry? = null,
    private val hubManager: AbstractDeviceHubManager = DeviceHubManagerImpl(context, config.dispatcher ?: Dispatchers.Default),
) : DeviceBase<D>(context, meta), CompositeControlComponent {

    protected val effectiveRegistry: ComponentRegistry? = registry ?: context.componentRegistry

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = spec.properties

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = spec.actions

    final override val messageBus: MutableSharedFlow<DeviceMessage>
        get() = hubManager.messageBus

    override fun toString(): String = "Device(id=$id, spec=$spec)"

    override val devices: Map<Name, Device>
        get() = hubManager.devices

    private val childConfigs: List<ChildComponentConfig<*>> = spec.childSpecs.values.toList()

    init {
        // Register action execution logic for local device
        spec.actions.values.forEach { actionSpec ->
            launch {
                val actionName = actionSpec.name
                messageFlow
                    .filterIsInstance<ActionExecuteMessage>()
                    .filter { it.action == actionName }
                    .onEach { msg ->
                        val result = execute(actionName, msg.argument)
                        messageBus.emit(
                            ActionResultMessage(
                                action = actionName,
                                result = result,
                                requestId = msg.requestId,
                                sourceDevice = id.asName()
                            )
                        )
                    }
                    .launchIn(this)
            }
        }
    }

    /**
     * Creates all child devices and waits for them if needed.
     */
    public suspend fun initChildren() {
        for (childCfg in childConfigs) {
            val childDevice = ConfigurableCompositeControlComponent(
                spec = childCfg.spec,
                registry = effectiveRegistry,
                context = context,
                meta = childCfg.meta ?: Meta.EMPTY,
                config = childCfg.config
            )
            hubManager.addDeviceSync(childCfg.name, childDevice, childCfg.config, childCfg.meta)
        }
    }

    /**
     * A factory method that creates a device and immediately calls [initChildren].
     */
    public companion object {
        public suspend fun <D : ConfigurableCompositeControlComponent<D>> create(
            spec: CompositeControlComponentSpec<D>,
            context: Context,
            meta: Meta = Meta.EMPTY,
            config: DeviceLifecycleConfig = DeviceLifecycleConfig(),
            registry: ComponentRegistry? = null,
        ): D {
            @Suppress("UNCHECKED_CAST")
            val device = ConfigurableCompositeControlComponent(
                spec = spec,
                context = context,
                meta = meta,
                config = config,
                registry = registry
            ) as D
            device.initChildren()
            return device
        }
    }

    override suspend fun onStart() {
        with(spec) {
            self.onOpen()
            validate(self)
        }
        // Autostart children if not LAZY
        hubManager.devices.values
            .filter { it.lifecycleState != LifecycleState.STARTED && it.lifecycleState != LifecycleState.STARTING }
            .forEach { child ->
                val mode = hubManager.childrenJobs[child.id.parseAsName()]?.lifecycleMode
                if (mode != LifecycleMode.LAZY) {
                    child.start()
                }
            }
    }

    override suspend fun onStop() {
        // Stop children, respecting each child's stopTimeout
        hubManager.devices.values.forEach { child ->
            launch(child.coroutineContext) {
                try{
                    val stopResult = withTimeoutOrNull(getChildStopTimeout(child)) {
                        child.stop()
                    }
                    if (stopResult == null) {
                        // calls onStopTimeout if child config has it
                        val job = hubManager.childrenJobs[child.id.parseAsName()]
                        if (job != null) {
                            hubManager.onStopTimeout(child.id.parseAsName(), job.config)
                        }
                    }
                } catch (ex: Throwable) {
                    context.logger.error(ex) { "Error while stopping child device ${child.id}" }
                }
            }
        }
        with(spec) {
            self.onClose()
        }
    }

    private fun getChildStopTimeout(device: Device): Duration {
        val job = hubManager.childrenJobs[device.id.parseAsName()]
        return job?.config?.stopTimeout ?: Duration.INFINITE
    }

    /**
     * Called when a child device is stopped in [AbstractDeviceHubManager].
     */
    internal open fun onChildStop() {}

    @Suppress("UNCHECKED_CAST")
    public fun <CD : ConfigurableCompositeControlComponent<CD>> getChildDevice(name: Name): CD {
        return (hubManager.devices[name] as? CD)
            ?: error("Child device $name not found or type mismatch")
    }

    public fun getChildMessageBus(name: Name): SharedFlow<DeviceMessage>? =
        hubManager.getChildMessageBus(name)

    public fun <CD : ConfigurableCompositeControlComponent<CD>> childDevice(name: Name? = null):
            PropertyDelegateProvider<ConfigurableCompositeControlComponent<D>, ReadOnlyProperty<ConfigurableCompositeControlComponent<D>, CD>> =
        PropertyDelegateProvider { _, property ->
            ReadOnlyProperty { _, _ ->
                val devName = name ?: property.name.asName()
                getChildDevice<CD>(devName)
            }
        }

    public inline operator fun <reified Dev : Device> get(name: Name): Dev? = devices[name] as? Dev
    public inline operator fun <reified Dev : Device> get(name: String): Dev? = this[name.asName()]
}

/**
 * Stops the device with a given [timeout].
 * Logs a warning if the timeout is reached.
 */
public suspend fun WithLifeCycle.stopWithTimeout(timeout: Duration = Duration.INFINITE) {
    val result = withTimeoutOrNull(timeout) {
        stop()
    }
    if (result == null) {
        (this as? DeviceBase<*>)?.logger?.warn { "Timeout on stop for device ${this.id}" }
    }
}
