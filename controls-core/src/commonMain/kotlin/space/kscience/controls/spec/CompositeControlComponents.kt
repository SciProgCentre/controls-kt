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
 * Defines different modes of how a child device is coupled to its parent device.
 *
 * @see LINKED
 * @see INDEPENDENT
 * @see LAZY
 */
public enum class LifecycleMode {
    /**
     * The child device is started and stopped automatically with the parent.
     */
    LINKED,

    /**
     * The child device is started and stopped independently of the parent.
     */
    INDEPENDENT,

    /**
     * The child device is created but starts only after an explicit request.
     */
    LAZY
}

/**
 * Allows loading an external configuration for a device.
 */
public interface ExternalConfigurationProvider {
    /**
     * Loads a [Meta] configuration for a device identified by [name].
     *
     * @param name The [Name] of the device.
     * @return The [Meta] containing the device configuration, or `null` if not found.
     */
    public suspend fun loadExternalConfig(name: Name): Meta?
}

/**
 * A basic interface for health-checking a [Device].
 *
 * Implementations should return `true` if the device is in a healthy state,
 * and `false` otherwise.
 */
public fun interface HealthChecker {
    /**
     * Checks whether the given [device] is healthy.
     *
     * @param device The device to be checked.
     * @return `true` if [device] is healthy, `false` otherwise.
     */
    public suspend fun isHealthy(device: Device): Boolean
}

/**
 * Defines error handling strategies for child devices.
 */
public enum class ChildDeviceErrorHandler {
    /**
     * Ignore errors from child devices.
     */
    IGNORE,

    /**
     * Automatically restart the child device when an error occurs.
     */
    RESTART,

    /**
     * If an error occurs in a child device, stop the parent device.
     */
    STOP_PARENT,

    /**
     * Propagate the error upwards.
     */
    PROPAGATE,

    /**
     * Custom user-defined strategy handled in [AbstractDeviceHubManager.onCustomError].
     */
    CUSTOM,
}

/**
 * Describes configuration for restart behavior.
 *
 * @property maxAttempts Maximum number of restart attempts before giving up.
 * @property delayBetweenAttempts Base delay before each restart attempt.
 * @property resetOnSuccess Whether to reset the restart-attempt counter on successful start.
 * @property strategy A [RestartStrategy] describing how the delay is calculated.
 */
public data class RestartPolicy(
    val maxAttempts: Int = Int.MAX_VALUE,
    val delayBetweenAttempts: Duration = Duration.ZERO,
    val resetOnSuccess: Boolean = true,
    val strategy: RestartStrategy = RestartStrategy.LINEAR,
)

/**
 * Defines how the delay is calculated for subsequent restart attempts.
 */
public enum class RestartStrategy {
    /**
     * Uses a fixed delay from [RestartPolicy.delayBetweenAttempts].
     */
    LINEAR,

    /**
     * Uses an exponential backoff strategy, doubling the delay each time.
     */
    EXPONENTIAL_BACKOFF,

    /**
     * Reserved for a custom or user-defined strategy.
     */
    CUSTOM,
}

/**
 * Represents various events or state changes for devices.
 */
public sealed class DeviceStateEvent {
    public abstract val deviceName: Name

    /**
     * Indicates that a device was added.
     */
    public data class DeviceAdded(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device was started.
     */
    public data class DeviceStarted(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device was stopped.
     */
    public data class DeviceStopped(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device was removed.
     */
    public data class DeviceRemoved(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device has failed.
     */
    public data class DeviceFailed(override val deviceName: Name, val error: Throwable) : DeviceStateEvent()

    /**
     * Indicates that a device was detached from the system.
     */
    public data class DeviceDetached(override val deviceName: Name) : DeviceStateEvent()
}

/**
 * Configuration for a device's lifecycle, including optional parameters such as timeouts
 * and error-handling strategies.
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
 * Allows external configuration for a [DeviceLifecycleConfigBuilder].
 */
public fun interface ExternalConfigApplier {
    /**
     * Apply external configuration to the provided builder.
     *
     * @param builder The builder that will be modified.
     * @param deviceName The device name for which the configuration is applied.
     */
    public suspend fun applyConfig(builder: DeviceLifecycleConfigBuilder, deviceName: Name)
}

/**
 * Builder for creating [DeviceLifecycleConfig] instances.
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

    /**
     * Loads an external configuration using the provided [externalApplier].
     *
     * @param deviceName The name of the device.
     * @param externalApplier The applier that modifies this builder based on external config.
     */
    public suspend fun applyExternalConfig(deviceName: Name, externalApplier: ExternalConfigApplier) {
        externalApplier.applyConfig(this, deviceName)
    }

    /**
     * Builds a [DeviceLifecycleConfig] instance using the current state of this builder.
     */
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
 * Sets [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.LINKED].
 */
public fun DeviceLifecycleConfigBuilder.linked() {
    lifecycleMode = LifecycleMode.LINKED
}

/**
 * Sets [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.INDEPENDENT].
 */
public fun DeviceLifecycleConfigBuilder.independent() {
    lifecycleMode = LifecycleMode.INDEPENDENT
}

/**
 * Sets [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.LAZY].
 */
public fun DeviceLifecycleConfigBuilder.lazy() {
    lifecycleMode = LifecycleMode.LAZY
}

/**
 * Sets [DeviceLifecycleConfigBuilder.onError] to [ChildDeviceErrorHandler.RESTART].
 */
public fun DeviceLifecycleConfigBuilder.restartOnError() {
    onError = ChildDeviceErrorHandler.RESTART
}

/**
 * Sets [DeviceLifecycleConfigBuilder.onError] to [ChildDeviceErrorHandler.PROPAGATE].
 */
public fun DeviceLifecycleConfigBuilder.propagateError() {
    onError = ChildDeviceErrorHandler.PROPAGATE
}

/**
 * Sets both [DeviceLifecycleConfigBuilder.startTimeout] and [DeviceLifecycleConfigBuilder.stopTimeout] to the provided [timeout].
 */
public fun DeviceLifecycleConfigBuilder.withCustomTimeout(timeout: Duration) {
    startTimeout = timeout
    stopTimeout = timeout
}

/**
 * Provides a registry for specifications.
 */
public interface ComponentRegistry : ContextAware {
    /**
     * Retrieves a [CompositeControlComponentSpec] by its [name].
     *
     * @param name The name of the spec to retrieve.
     * @return The spec if found, or `null` otherwise.
     */
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

    /**
     * Registers a [CompositeControlComponentSpec] with the given [name].
     */
    public fun registerSpec(spec: CompositeControlComponentSpec<*>, name: Name) {
        specs[name] = spec
    }

    public companion object : PluginFactory<ComponentRegistryManager> {
        override val tag: PluginTag = PluginTag("controls.spechub", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): ComponentRegistryManager = ComponentRegistryManager()
    }
}

/**
 * Extension property to retrieve the [ComponentRegistry] from the [Context].
 */
public val Context.componentRegistry: ComponentRegistry?
    get() = plugins[ComponentRegistryManager]

/**
 * Convenience method to install a [ComponentRegistryManager] in the [ContextBuilder].
 */
public fun ContextBuilder.withSpecHub() {
    plugin(ComponentRegistryManager)
}

/**
 * Represents configuration for a child component.
 *
 * @param CD The type of the child device.
 */
public interface ChildComponentConfig<CD : ConfigurableCompositeControlComponent<CD>> {
    public val spec: CompositeControlComponentSpec<CD>
    public val config: DeviceLifecycleConfig
    public val meta: Meta?
    public val name: Name
}

/**
 * Base interface describing a composite device specification.
 *
 * @param D The type of device.
 */
public interface CompositeDeviceSpec<D : ConfigurableCompositeControlComponent<D>> {
    public val properties: Map<String, DevicePropertySpec<D, *>>
    public val actions: Map<String, DeviceActionSpec<D, *, *>>
    public val childSpecs: Map<String, ChildComponentConfig<*>>

    /**
     * A lifecycle hook called when the device is opening.
     */
    public suspend fun D.onOpen()

    /**
     * A lifecycle hook called when the device is closing.
     */
    public suspend fun D.onClose()

    /**
     * Validates the specified [device].
     *
     * @throws IllegalStateException If validation fails.
     */
    public fun validate(device: D)

    /**
     * Registers a [deviceProperty].
     */
    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P

    /**
     * Registers a [deviceAction].
     */
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    /**
     * Creates a [PropertyDescriptor].
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
     */
    public fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        property: KProperty<*>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit
    ): ActionDescriptor

    /**
     * Creates a read-only property with the given [converter].
     */
    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>>

    /**
     * Creates a mutable property with the given [converter].
     */
    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>>

    /**
     * Creates an action with the specified input and output converters.
     */
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
     * Declares a child specification for a device.
     *
     * @param fallbackSpec The fallback specification if none is found in the registry.
     * @param specKeyInRegistry The key under which this spec might be found in the registry.
     * @param childDeviceName An optional name for the child device.
     * @param metaBuilder Optional builder for the child's [Meta].
     * @param configBuilder Optional builder for the child's [DeviceLifecycleConfig].
     */
    public fun <CDS : CompositeControlComponentSpec<CD>, CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        fallbackSpec: CDS,
        specKeyInRegistry: Name? = null,
        childDeviceName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {},
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            val registryKey = specKeyInRegistry ?: property.name.asName()
            val childName = childDeviceName ?: property.name.asName()
            val config = DeviceLifecycleConfigBuilder().apply(configBuilder).build()
            val meta = metaBuilder?.let { Meta(it) }
            val fromRegistry: CompositeControlComponentSpec<CD>? = thisRef.registry?.getSpec<CD>(registryKey)

            val foundSpec: CompositeControlComponentSpec<CD> = fromRegistry ?: fallbackSpec

            val mapKey = childName.toString()
            check(thisRef.childSpecMap[mapKey] == null) {
                "Child spec with name '$mapKey' is already registered in $thisRef."
            }

            val childConfig = object : ChildComponentConfig<CD> {
                override val spec: CompositeControlComponentSpec<CD> = foundSpec
                override val config: DeviceLifecycleConfig = config
                override val meta: Meta? = meta
                override val name: Name = childName
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
                    withContext(device.coroutineContext) { device.execute(input) }
            })
            ReadOnlyProperty { _, _ -> devAction }
        }
}

/**
 * Defines an action with a `Unit` input and a `Unit` output.
 *
 * @param descriptorBuilder Optional builder for the action descriptor.
 * @param name An optional name for the action.
 * @param execute The block to execute when this action is invoked.
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
 * Defines an action with a [Meta] input and a [Meta] output.
 *
 * @param descriptorBuilder Optional builder for the action descriptor.
 * @param name An optional name for the action.
 * @param execute The block to execute with a [Meta] input, producing a [Meta] output.
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
 * An abstract manager for devices, handling lifecycle, error policies, transactions, etc.
 *
 * @param context The [Context] used for logging and plugin management.
 * @param dispatcher The [CoroutineDispatcher] for coroutines.
 */
public abstract class AbstractDeviceHubManager(
    public val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * The supervisor job that manages all child device coroutines.
     */
    protected val parentJob: Job = SupervisorJob()

    private val childLock = Mutex()

    internal val childrenJobs: MutableMap<Name, ChildJob> = mutableMapOf()

    /**
     * A map of current devices keyed by [Name].
     */
    public val devices: Map<Name, Device>
        get() = childrenJobs.mapValues { it.value.device }

    /**
     * A flow for broadcasting [DeviceMessage]s.
     */
    public abstract val messageBus: MutableSharedFlow<DeviceMessage>

    /**
     * A flow for system-level events and logs.
     */
    public abstract val systemBus: MutableSharedFlow<SystemLogMessage>

    /**
     * A flow for [DeviceStateEvent] changes.
     */
    public abstract val deviceChanges: MutableSharedFlow<DeviceStateEvent>

    /**
     * Tracks the number of restart attempts per device.
     */
    internal val restartAttemptsMap: MutableMap<Name, Int> = mutableMapOf()

    /**
     * Represents a running child device along with its job, config, and message buses.
     */
    internal data class ChildJob(
        val device: Device,
        val job: Job,
        val config: DeviceLifecycleConfig,
        val messageBus: MutableSharedFlow<DeviceMessage>,
        val systemBus: MutableSharedFlow<SystemLogMessage>,
        val meta: Meta? = null,
        val reuseBus: Boolean = false
    ) {
        val lifecycleMode: LifecycleMode get() = config.lifecycleMode
    }

    /**
     * Handles errors from a child device.
     */
    protected open suspend fun onChildErrorCaught(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Error in child device $childName with policy ${config.onError}" }
    }

    /**
     * Stops the parent if a child error triggers STOP_PARENT policy.
     */
    protected open suspend fun onParentStopRequested(ex: Throwable, childName: Name) {
        context.logger.error(ex) { "Stopping parent due to error in child $childName" }
        parentJob.cancelAndJoin()
    }

    /**
     * Invoked for a custom error strategy if [ChildDeviceErrorHandler.CUSTOM] is set.
     */
    protected open suspend fun onCustomError(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Custom error strategy for device $childName: override onCustomError." }
    }

    /**
     * Called if a device times out during startup.
     */
    protected open suspend fun onStartTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.error { "Timeout while starting $deviceName." }
        throw RuntimeException("Timeout on start for $deviceName")
    }

    /**
     * Called if a device times out during stopping.
     */
    internal open suspend fun onStopTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.warn { "Timeout while stopping $deviceName. Consider overriding onStopTimeout." }
    }

    /**
     * Optionally performs a health check if [DeviceLifecycleConfig.healthChecker] is present.
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
        reuseBus: MutableSharedFlow<DeviceMessage>? = null
    ): ChildJob {
        val childMessageBus = reuseBus ?: MutableSharedFlow(
            replay = config.messageBuffer,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val childScope = config.coroutineScope ?: CoroutineScope(parentJob + dispatcher)

        val childJob = childScope.launch(CoroutineName("Child device $name")) {
            try {
                // Attempt to auto-start if not independent
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
                // If ended, the device is considered stopped
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
     * Adds a device asynchronously (does not wait until it starts fully).
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
     * Adds a device synchronously, TODO: wait until it starts.
     */
    public suspend fun addDeviceSync(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null
    ) {
        addDevice(name, device, config, meta)
    }

    /**
     * Removes a device asynchronously (non-blocking). Does not wait for it to stop fully.
     */
    public suspend fun removeDevice(name: Name) {
        childLock.withLock {
            removeDeviceUnlocked(name, waitStop = false)
        }
    }

    /**
     * Restarts a device, preserving its [DeviceLifecycleConfig] and [Meta].
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
     * Changes the [LifecycleMode] for the specified device.
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
     * Replaces a device ("hot swap") optionally reusing its message bus.
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
     * Removes a device, optionally waiting until it is fully stopped.
     *
     * @param waitStop If `true`, waits for stopping within [DeviceLifecycleConfig.stopTimeout].
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
            // Do not wait, just launch the stop in a new coroutine
            CoroutineScope(parentJob + dispatcher).launch {
                try {
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

    /**
     * Retrieves the [MutableSharedFlow] for messages belonging to a specific child device.
     *
     * @param name The name of the child device.
     * @return The child's message bus, or `null` if the device is not found.
     */
    public fun getChildMessageBus(name: Name): MutableSharedFlow<DeviceMessage>? = childrenJobs[name]?.messageBus

    /**
     * A hook called by [removeJobFromRegistry] after a child device is stopped.
     */
    internal open fun onChildStop() {}

    /**
     * Starts multiple devices in a transactional manner.
     *
     * If any device fails to start, all previously started devices are stopped (rollback).
     *
     * @param deviceNames The list of device names to start.
     * @return `true` if all devices were started successfully, `false` otherwise.
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
     *
     * If any device fails to stop, an attempt is made to restart the already stopped devices.
     *
     * @param deviceNames The list of device names to stop.
     * @return `true` if all devices were stopped successfully, `false` otherwise.
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
            // rollback
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
     * Placeholder method for setting up a distributed transport or broker for messages.
     *
     * By default, it only logs an informational message.
     */
    public open fun installDistributedTransport() {
        context.logger.info { "installDistributedTransport: implement broker here." }
    }
}

/**
 * A default implementation of [AbstractDeviceHubManager] with extra event flows.
 *
 * @param context The parent [Context].
 * @param dispatcher The [CoroutineDispatcher] used for child coroutines.
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
 * Represents a device capable of hosting child devices.
 */
public interface CompositeControlComponent : Device {
    /**
     * The message bus for device-level messages.
     */
    public val messageBus: SharedFlow<DeviceMessage>

    /**
     * A map of child devices keyed by their [Name].
     */
    public val devices: Map<Name, Device>
}

/**
 * A [Device] that supports a composite structure of child components.
 *
 * @param D The concrete device type, extending [ConfigurableCompositeControlComponent].
 * @param spec The [CompositeControlComponentSpec] for this device.
 * @param context The [Context] for logging and plugin management.
 * @param meta The device's metadata.
 * @param config The [DeviceLifecycleConfig].
 * @param registry An optional [ComponentRegistry].
 * @param hubManager The [AbstractDeviceHubManager] responsible for device lifecycle.
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
        // Register action execution logic for this device
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
     * Instantiates all child devices configured in [spec.childSpecs] and adds them to the hub.
     */
    public suspend fun initChildren() {
        for (childCfg in childConfigs) {
            val spec = childCfg.spec

            val childDevice: ConfigurableCompositeControlComponent<*> = if (spec is DeviceSpecification<*>) {
                val instance = spec.deviceFactory(context, childCfg.meta ?: Meta.EMPTY)
                instance
            } else {
                ConfigurableCompositeControlComponent(
                    spec,
                    context,
                    childCfg.meta ?: Meta.EMPTY,
                    childCfg.config,
                    effectiveRegistry
                )
            }
            hubManager.addDeviceSync(childCfg.name, childDevice, childCfg.config, childCfg.meta)
        }
    }

    override suspend fun onStart() {
        with(spec) {
            self.onOpen()
            validate(self)
        }
        // Automatically start child devices if they are not LAZY
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
        // Stop each child device respecting its stopTimeout
        hubManager.devices.values.forEach { child ->
            launch(child.coroutineContext) {
                try {
                    val stopResult = withTimeoutOrNull(getChildStopTimeout(child)) {
                        child.stop()
                    }
                    if (stopResult == null) {
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
     * Called after a child device is stopped within [AbstractDeviceHubManager].
     */
    internal open fun onChildStop() {}

    /**
     * Retrieves a child device by [name].
     *
     * @throws IllegalStateException If the child device does not exist or type mismatches.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <CD : ConfigurableCompositeControlComponent<CD>> getChildDevice(name: Name): CD {
        return (hubManager.devices[name] as? CD)
            ?: error("Child device $name not found or type mismatch")
    }

    /**
     * Retrieves the message bus for a child device.
     */
    public fun getChildMessageBus(name: Name): SharedFlow<DeviceMessage>? =
        hubManager.getChildMessageBus(name)

    /**
     * A property delegate to obtain a child device by [name].
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
     * Operator to get a child device by [Name].
     */
    public inline operator fun <reified Dev : Device> get(name: Name): Dev? = devices[name] as? Dev

    /**
     * Operator to get a child device by a [String]-based name.
     */
    public inline operator fun <reified Dev : Device> get(name: String): Dev? = this[name.asName()]
}

/**
 * Stops the device within a given [timeout].
 * Logs a warning if the timeout expires.
 *
 * @receiver A [WithLifeCycle] device.
 * @param timeout The maximum time to wait for stopping.
 */
public suspend fun WithLifeCycle.stopWithTimeout(timeout: Duration = Duration.INFINITE) {
    val result = withTimeoutOrNull(timeout) {
        stop()
    }
    if (result == null) {
        (this as? DeviceBase<*>)?.logger?.warn { "Timeout on stop for device ${this.id}" }
    }
}

public abstract class DeviceSpecification<D : ConfigurableCompositeControlComponent<D>>(
    public val deviceFactory: (Context, Meta) -> D
) : CompositeControlComponentSpec<D>()

public inline fun <reified D : ConfigurableCompositeControlComponent<D>> typedSpec(
    noinline factory: (Context, Meta) -> D,
): DeviceSpecification<D> = object : DeviceSpecification<D>(factory) {}