@file:Suppress("MemberVisibilityCanBePrivate", "UNUSED_PARAMETER", "unused")

package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.dataforge.context.*
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.*
import kotlin.math.pow
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Defines different modes of how a child device is coupled to its parent device.
 *
 * - [LINKED] - The child device is started/stopped with the parent.
 * - [INDEPENDENT] - The child device must be started/stopped manually, and does not follow the parent's lifecycle.
 * - [LAZY] - The child device is created, but starts only upon explicit request.
 */
public enum class LifecycleMode {
    LINKED,
    INDEPENDENT,
    LAZY
}

/**
 * Allows loading an external configuration for a device.
 * The implementation might, for example, fetch configuration from a database or file.
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
 * A functional interface to perform a health check on a [Device].
 * Return `true` if the device is healthy, `false` otherwise.
 *
 * Implementations may be scheduled to run periodically, or called on demand.
 */
public fun interface HealthChecker {
    /**
     * Checks whether the given [device] is healthy.
     *
     * @param device The device to be checked.
     * @return `true` if the device is healthy, `false` otherwise.
     */
    public suspend fun isHealthy(device: Device): Boolean
}

/**
 * Defines error handling strategies for child devices.
 *
 * @see [IGNORE]
 * @see [RESTART]
 * @see [STOP_PARENT]
 * @see [PROPAGATE]
 * @see [CUSTOM]
 */
public enum class ChildDeviceErrorHandler {
    /**
     * Ignore errors from child devices (log them, but continue).
     */
    IGNORE,

    /**
     * Automatically restart the child device when an error occurs.
     * A [RestartPolicy] can be provided in [DeviceLifecycleConfig] to tune the behavior.
     */
    RESTART,

    /**
     * If a child device fails, stop the parent device as well.
     */
    STOP_PARENT,

    /**
     * Propagate the error upwards (e.g. rethrow).
     * This may cancel the parent coroutine if not handled further up.
     */
    PROPAGATE,

    /**
     * Custom user-defined strategy, handled in [AbstractDeviceHubManager.onCustomError].
     */
    CUSTOM,
}

/**
 * Describes how restarts are attempted if a device fails and [ChildDeviceErrorHandler.RESTART] is used.
 *
 * @property maxAttempts Maximum number of restart attempts before giving up.
 * @property delayBetweenAttempts Base delay before each restart attempt.
 * @property resetOnSuccess If `true`, resets the attempt counter to 0 upon a successful start.
 * @property strategy A [RestartStrategy] describing how the delay is calculated.
 */
public data class RestartPolicy(
    val maxAttempts: Int = Int.MAX_VALUE,
    val delayBetweenAttempts: Duration = Duration.ZERO,
    val resetOnSuccess: Boolean = true,
    val strategy: RestartStrategy = RestartStrategy.LINEAR,
) {
    public companion object {
        /**
         * The default restart policy:
         * - up to 5 attempts
         * - 2 seconds delay
         * - linear strategy
         * - reset attempts on success
         */
        public val DEFAULT: RestartPolicy = RestartPolicy(
            maxAttempts = 5,
            delayBetweenAttempts = 2.seconds,
            resetOnSuccess = true,
            strategy = RestartStrategy.LINEAR
        )
    }
}

/**
 * Defines how the delay is calculated for subsequent restart attempts.
 *
 * @see [LINEAR]
 * @see [EXPONENTIAL_BACKOFF]
 * @see [CUSTOM]
 */
public enum class RestartStrategy {
    /**
     * Uses the fixed [RestartPolicy.delayBetweenAttempts].
     */
    LINEAR,

    /**
     * Uses an exponential backoff strategy, e.g. delay * 2^(attempt-1).
     */
    EXPONENTIAL_BACKOFF,

    /**
     * Reserved for a custom or user-defined strategy (override code to handle).
     */
    CUSTOM,
}

/**
 * Represents various events or state changes for devices managed by a [AbstractDeviceHubManager].
 */
public sealed class DeviceStateEvent {
    public abstract val deviceName: Name

    /**
     * Indicates that a device was added to the manager.
     */
    public data class DeviceAdded(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device started.
     */
    public data class DeviceStarted(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device stopped.
     */
    public data class DeviceStopped(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device was removed from the manager.
     */
    public data class DeviceRemoved(override val deviceName: Name) : DeviceStateEvent()

    /**
     * Indicates that a device has failed due to some [error].
     */
    public data class DeviceFailed(override val deviceName: Name, val error: Throwable) : DeviceStateEvent()

    /**
     * Indicates that a device was detached from the system (physically or logically).
     * Typically fired after the internal job or scope is cancelled.
     */
    public data class DeviceDetached(override val deviceName: Name) : DeviceStateEvent()
}

/**
 * Configuration for a device's lifecycle, including optional parameters such as timeouts
 * and error-handling strategies.
 *
 * @param lifecycleMode [LifecycleMode] of the device.
 * @param messageBuffer The buffer size for the child's message flow.
 * @param startDelay An additional delay before starting the device.
 * @param startTimeout Timeout for starting the device.
 * @param stopTimeout Timeout for stopping the device.
 * @param coroutineScope An optional [CoroutineScope] in which this device runs.
 * @param dispatcher An optional [CoroutineDispatcher] for concurrency.
 * @param onError An [ChildDeviceErrorHandler] strategy.
 * @param healthChecker An optional [HealthChecker].
 * @param restartPolicy A [RestartPolicy] (used if onError == RESTART).
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
    val restartPolicy: RestartPolicy = RestartPolicy.DEFAULT,
) {
    init {
        require(messageBuffer > 0) { "Message buffer size must be positive." }
        startTimeout?.let { require(!it.isNegative()) { "Start timeout must be non-negative." } }
        stopTimeout?.let { require(!it.isNegative()) { "Stop timeout must be non-negative." } }
    }
}

/**
 * A functional interface that can apply external configs to a [DeviceLifecycleConfigBuilder].
 */
public fun interface ExternalConfigApplier {
    /**
     * Applies some external configuration to the provided [builder]
     * specifically for the device named [deviceName].
     */
    public suspend fun applyConfig(builder: DeviceLifecycleConfigBuilder, deviceName: Name)
}

/**
 * A builder for [DeviceLifecycleConfig].
 * One can manually set properties or apply an [ExternalConfigApplier].
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
    public var restartPolicy: RestartPolicy = RestartPolicy.DEFAULT

    /**
     * Load and apply external configuration via [externalApplier].
     */
    public suspend fun applyExternalConfig(deviceName: Name, externalApplier: ExternalConfigApplier) {
        externalApplier.applyConfig(this, deviceName)
    }

    /**
     * Builds the resulting [DeviceLifecycleConfig].
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
        restartPolicy = restartPolicy,
    )
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] = [LifecycleMode.LINKED].
 */
public fun DeviceLifecycleConfigBuilder.linked() {
    lifecycleMode = LifecycleMode.LINKED
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] = [LifecycleMode.INDEPENDENT].
 */
public fun DeviceLifecycleConfigBuilder.independent() {
    lifecycleMode = LifecycleMode.INDEPENDENT
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] = [LifecycleMode.LAZY].
 */
public fun DeviceLifecycleConfigBuilder.lazy() {
    lifecycleMode = LifecycleMode.LAZY
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.onError] = [ChildDeviceErrorHandler.RESTART].
 */
public fun DeviceLifecycleConfigBuilder.restartOnError() {
    onError = ChildDeviceErrorHandler.RESTART
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.onError] = [ChildDeviceErrorHandler.PROPAGATE].
 */
public fun DeviceLifecycleConfigBuilder.propagateError() {
    onError = ChildDeviceErrorHandler.PROPAGATE
}

/**
 * Sets both [DeviceLifecycleConfigBuilder.startTimeout] and [DeviceLifecycleConfigBuilder.stopTimeout]
 * to the provided [timeout].
 */
public fun DeviceLifecycleConfigBuilder.withCustomTimeout(timeout: Duration) {
    startTimeout = timeout
    stopTimeout = timeout
}

/**
 * Provides a registry for specifications (e.g., device specs).
 */
public interface ComponentRegistry : ContextAware {
    /**
     * Retrieves a [CompositeControlComponentSpec] by its [name].
     * Return `null` if not found or if the class cast fails.
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>?
}

/**
 * A default plugin-based manager for specs.
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
 * Extension property to retrieve the [ComponentRegistry] from the [Context], if installed.
 */
public val Context.componentRegistry: ComponentRegistry?
    get() = plugins[ComponentRegistryManager]

/**
 * Convenience for [ContextBuilder]: install a [ComponentRegistryManager] plugin.
 */
public fun ContextBuilder.withSpecHub() {
    plugin(ComponentRegistryManager)
}

/**
 * Represents configuration for a child component (sub-device).
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
 * It declares properties and actions, as well as potential child specs.
 *
 * @param D The type of device using this spec.
 */
public interface CompositeDeviceSpec<D : ConfigurableCompositeControlComponent<D>> {
    public val properties: Map<String, DevicePropertySpec<D, *>>
    public val actions: Map<String, DeviceActionSpec<D, *, *>>
    public val childSpecs: Map<String, ChildComponentConfig<*>>

    /**
     * Called when the device is opening (starting). Override if needed.
     */
    public suspend fun D.onOpen()

    /**
     * Called when the device is closing (stopping). Override if needed.
     */
    public suspend fun D.onClose()

    /**
     * Validate the [device] state or properties. If validation fails, throw.
     */
    public fun validate(device: D)

    /**
     * Register a [deviceProperty].
     */
    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P

    /**
     * Register a [deviceAction].
     */
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    /**
     * Creates a [PropertyDescriptor] internally.
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
     * Declares a read-only property with the given [converter].
     */
    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>>

    /**
     * Declares a mutable property with the given [converter].
     */
    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit,
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>>

    /**
     * Declares an action with the specified input and output converters, plus an execution block.
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
 *
 * @param D The type of device.
 * @param registry (optional) a [ComponentRegistry] to lookup child specs by name.
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

    override suspend fun D.onOpen() {
        // default no-op
    }

    override suspend fun D.onClose() {
        // default no-op
    }

    override fun validate(device: D) {
        // Verify that all declared properties and actions are indeed in the device's descriptors
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
        read: suspend D.(propertyName: String) -> T?,
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
     * A convenience for declaring a child specification. This references either a fallback spec
     * or tries to retrieve one from [registry] by [specKeyInRegistry].
     *
     * @param fallbackSpec The spec to use if not found in the registry.
     * @param specKeyInRegistry The name key in the registry, if any.
     * @param childDeviceName The actual name of the child device (defaults to the property name).
     * @param metaBuilder Builds [Meta] for the child.
     * @param configBuilder Builds [DeviceLifecycleConfig] for the child.
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
            val cName = childDeviceName ?: property.name.asName()
            val config = DeviceLifecycleConfigBuilder().apply(configBuilder).build()
            val meta = metaBuilder?.let { Meta(it) }
            val fromRegistry: CompositeControlComponentSpec<CD>? = thisRef.registry?.getSpec<CD>(registryKey)
            val foundSpec: CompositeControlComponentSpec<CD> = fromRegistry ?: fallbackSpec
            val mapKey = cName.toString()
            check(thisRef.childSpecMap[mapKey] == null) {
                "Child spec with name '$mapKey' is already registered in $thisRef."
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
                    withContext(device.coroutineContext) { device.execute(input) }
            })
            ReadOnlyProperty { _, _ -> devAction }
        }
}

/**
 * Declares an action with a `Unit` input and a `Unit` output.
 *
 * @param descriptorBuilder Configure the action's metadata if needed.
 * @param name Optionally override the action's name (defaults to the property name).
 * @param execute A suspend function that is called with no input and produces no output.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.unitAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.() -> Unit,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    action(MetaConverter.unit, MetaConverter.unit, descriptorBuilder, name) { execute() }

/**
 * Declares an action with a [Meta] input and a [Meta] output.
 *
 * @param descriptorBuilder Configure the action's metadata if needed.
 * @param name Optionally override the action's name (defaults to the property name).
 * @param execute A suspend function that takes [Meta] as input and returns a [Meta] output.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(Meta) -> Meta,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    action(MetaConverter.meta, MetaConverter.meta, descriptorBuilder, name) { execute(it) }

/**
 * An abstract manager for devices, handling lifecycle, error policies, transactions, etc.
 *
 * Typically, you extend or instantiate this to manage a set of child [Device]s.
 *
 * @param context The [Context] for logging and plugin management.
 * @param dispatcher A [CoroutineDispatcher] for concurrency; default is [Dispatchers.Default].
 */
public abstract class AbstractDeviceHubManager(
    public val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * The supervisor job for all child devices.
     */
    protected val parentJob: Job = SupervisorJob()

    private val childLock = Mutex()

    /**
     * Internal map that keeps track of each child's [ChildJob].
     */
    internal val childrenJobs: MutableMap<Name, ChildJob> = mutableMapOf()

    /**
     * A map of current devices keyed by [Name].
     */
    public val devices: Map<Name, Device>
        get() = childrenJobs.mapValues { it.value.device }

    /**
     * A [MutableSharedFlow] for broadcasting (or replaying) [DeviceMessage]s from all devices.
     */
    public abstract val messageBus: MutableSharedFlow<DeviceMessage>

    /**
     * A [MutableSharedFlow] for system-level or log messages.
     */
    public abstract val systemBus: MutableSharedFlow<SystemLogMessage>

    /**
     * A [MutableSharedFlow] for [DeviceStateEvent] changes (e.g. device added/removed/failed).
     */
    public abstract val deviceChanges: MutableSharedFlow<DeviceStateEvent>

    /**
     * Tracks the number of restart attempts per device.
     */
    internal val restartAttemptsMap: MutableMap<Name, Int> = mutableMapOf()

    /**
     * A set that indicates which devices are currently in the middle of a RESTART procedure,
     * to avoid multiple concurrent restarts for the same device.
     */
    private val restartingDevices: MutableSet<Name> = mutableSetOf()

    /**
     * Represents a running child device along with its job, config, and flows.
     *
     * @property device The managed device instance.
     * @property collectorJob The coroutine job collecting messages from [device.messageFlow].
     * @property config The lifecycle config for this device.
     * @property messageBus A bus dedicated to this child. By default, it is unique unless [reuseBus] is `true`.
     * @property systemBus The system-level bus (shared).
     * @property meta The optional metadata for the device.
     * @property reuseBus If `true`, reuses the old bus upon hot-swap.
     */
    internal data class ChildJob(
        val device: Device,
        val collectorJob: Job,
        val config: DeviceLifecycleConfig,
        val messageBus: MutableSharedFlow<DeviceMessage>,
        val systemBus: MutableSharedFlow<SystemLogMessage>,
        val meta: Meta? = null,
        val reuseBus: Boolean = false
    ) {
        val lifecycleMode: LifecycleMode get() = config.lifecycleMode
    }

    /**
     * Called when an error is thrown from a child's message-collecting coroutine or any other child logic.
     *
     * By default, logs the error.
     * Override to provide custom reaction (before applying the [onError] policy).
     */
    protected open suspend fun onChildErrorCaught(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Error in child device $childName with policy ${config.onError}" }
    }

    /**
     * Called if a child error triggers [ChildDeviceErrorHandler.STOP_PARENT], indicating the parent must stop.
     *
     * The default simply cancels the [parentJob].
     */
    protected open suspend fun onParentStopRequested(ex: Throwable, childName: Name) {
        context.logger.error(ex) { "Stopping parent due to error in child $childName" }
        parentJob.cancelAndJoin()
    }

    /**
     * Called if [ChildDeviceErrorHandler.CUSTOM] is used.
     * Override to implement a custom strategy for errors.
     */
    protected open suspend fun onCustomError(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Custom error strategy for device $childName: override onCustomError if needed." }
    }

    /**
     * Called when a device times out while starting.
     * By default, throws a runtime exception with a message.
     */
    protected open suspend fun onStartTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        val msg = "Timeout while starting $deviceName."
        context.logger.error { msg }
        throw RuntimeException(msg)
    }

    /**
     * Called when a device times out while stopping.
     * By default, logs a warning.
     */
    internal open suspend fun onStopTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.warn { "Timeout while stopping $deviceName. You may override onStopTimeout if needed." }
    }

    /**
     * Performs a health check if [DeviceLifecycleConfig.healthChecker] is present.
     * If check fails, emits [DeviceStateEvent.DeviceFailed].
     *
     * By default, this is not scheduled automatically; one might call it manually or from a timer.
     */
    internal open suspend fun checkHealth(child: ChildJob) {
        val hc = child.config.healthChecker ?: return
        if (!hc.isHealthy(child.device)) {
            val ex = RuntimeException("Health check failed for device ${child.device.id}")
            deviceChanges.emit(DeviceStateEvent.DeviceFailed(child.device.id.parseAsName(), ex))
        }
    }

    /**
     * Launches a coroutine to collect [device.messageFlow].
     * The device is not automatically started here. This only sets up the collector job.
     *
     * @param reuseBus If this child is being hot-swapped, we may reuse the old message bus.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun launchChild(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        reuseBus: MutableSharedFlow<DeviceMessage>? = null
    ): ChildJob {
        val childMessageBus = reuseBus ?: MutableSharedFlow(
            replay = config.messageBuffer,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val childScope = config.coroutineScope ?: CoroutineScope(parentJob + dispatcher)

        val collectorJob = childScope.launch(CoroutineName("Collect device $name")) {
            try {
                device.messageFlow.collect { msg ->
                    val wrapped = msg.changeSource { name.plus(it) }
                    childMessageBus.emit(wrapped)
                    messageBus.emit(wrapped)
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                onChildErrorCaught(ex, name, config)
                deviceChanges.emit(DeviceStateEvent.DeviceFailed(name, ex))
                messageBus.emit(DeviceMessage.error(ex, name))

                handleErrorPolicy(ex, name, config)
            } finally {
                if (!isActive) {
                    removeJobFromRegistry(name, device, childMessageBus)
                    deviceChanges.emit(DeviceStateEvent.DeviceStopped(name))
                }
            }
        }
        return ChildJob(device, collectorJob, config, childMessageBus, systemBus, meta, reuseBus != null)
    }

    /**
     * Handles the [onError] policy from [DeviceLifecycleConfig].
     */
    private suspend fun handleErrorPolicy(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        when (config.onError) {
            ChildDeviceErrorHandler.IGNORE -> {}
            ChildDeviceErrorHandler.RESTART -> {
                scheduleRestart(childName, config.restartPolicy)
            }
            ChildDeviceErrorHandler.STOP_PARENT -> onParentStopRequested(ex, childName)
            ChildDeviceErrorHandler.PROPAGATE -> throw ex
            ChildDeviceErrorHandler.CUSTOM -> onCustomError(ex, childName, config)
        }
    }

    /**
     * Schedules a restart for [childName] according to [policy].
     */
    private suspend fun scheduleRestart(childName: Name, policy: RestartPolicy) {
        childLock.withLock {
            if (childName in restartingDevices) {
                return
            }
            restartingDevices.add(childName)
        }

        try {
            val attempts = (restartAttemptsMap[childName] ?: 0) + 1
            restartAttemptsMap[childName] = attempts

            if (attempts > policy.maxAttempts) {
                context.logger.warn { "Max restart attempts exceeded for $childName." }
                return
            }

            val delayDuration = calculateDelay(policy, attempts)
            if (delayDuration > Duration.ZERO) {
                delay(delayDuration)
            }

            restartDevice(childName)

        } finally {
            childLock.withLock {
                restartingDevices.remove(childName)
            }
        }
    }

    private fun calculateDelay(policy: RestartPolicy, attempts: Int): Duration {
        return when (policy.strategy) {
            RestartStrategy.LINEAR -> policy.delayBetweenAttempts
            RestartStrategy.EXPONENTIAL_BACKOFF -> {
                policy.delayBetweenAttempts * 2.0.pow((attempts - 1).toDouble())
            }
            RestartStrategy.CUSTOM -> Duration.ZERO // user-override
        }
    }

    /**
     * Removes the job from [childrenJobs] if it matches the [device], and emits [DeviceStateEvent.DeviceDetached].
     * Also resets replay cache for the child bus.
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
     * Add a device asynchronously, i.e. does **not** wait for the device to fully start.
     *
     * @param name The name of the new device.
     * @param device The [Device] instance.
     * @param config The [DeviceLifecycleConfig].
     * @param meta Optional [Meta].
     */
    public suspend fun addDevice(name: Name, device: Device, config: DeviceLifecycleConfig, meta: Meta? = null) {
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
     * Add a device **synchronously**: it waits for the device to start (unless [LifecycleMode.INDEPENDENT]).
     *
     * @param name The name of the new device.
     * @param device The [Device] instance.
     * @param config The [DeviceLifecycleConfig].
     * @param meta Optional [Meta].
     * @throws RuntimeException if the start times out.
     */
    public suspend fun addDeviceSync(name: Name, device: Device, config: DeviceLifecycleConfig, meta: Meta? = null) {
        addDevice(name, device, config, meta)
        if (config.lifecycleMode == LifecycleMode.INDEPENDENT) {
            return
        }
        finalizeDeviceStart(name, config, device)
    }

    /**
     * Called by [addDeviceSync] to do the actual start with a potential [startDelay] and [startTimeout].
     */
    private suspend fun finalizeDeviceStart(name: Name, config: DeviceLifecycleConfig, device: Device) {
        // If there's a requested startDelay, we do it now
        if (config.startDelay > Duration.ZERO) {
            delay(config.startDelay)
        }
        val startTimeout = config.startTimeout ?: Duration.INFINITE

        val success = withTimeoutOrNull(startTimeout) {
            device.start()
        }
        if (success == null) {
            onStartTimeout(name, config)
        } else {
            deviceChanges.emit(DeviceStateEvent.DeviceStarted(name))
            // If the policy says so, reset attempt count
            if (config.restartPolicy.resetOnSuccess) {
                restartAttemptsMap[name] = 0
            }
        }
    }

    /**
     * Remove a device asynchronously (non-blocking).
     * It does not wait for the device to fully stop if [waitStop] = false.
     */
    public suspend fun removeDevice(name: Name) {
        childLock.withLock {
            removeDeviceUnlocked(name, waitStop = false)
        }
    }

    /**
     * Remove a device **synchronously**: it waits for the device to fully stop.
     */
    public suspend fun removeDeviceSync(name: Name) {
        childLock.withLock {
            removeDeviceUnlocked(name, waitStop = true)
        }
    }

    /**
     * Restarts a device, preserving its [DeviceLifecycleConfig] and [Meta].
     * This method will stop the device if it is running, and then relaunch it.
     *
     * @param name The name of the device to restart.
     */
    public suspend fun restartDevice(name: Name) {
        childLock.withLock {
            val old = childrenJobs[name] ?: return
            removeDeviceUnlocked(name, waitStop = true)
            val newChild = launchChild(
                name,
                old.device,
                old.config,
                old.meta,
                reuseBus = if (old.reuseBus) old.messageBus else null
            )
            childrenJobs[name] = newChild
            systemBus.emit(SystemLogMessage("Device $name restarted", sourceDevice = name))
        }
        // If device is not INDEPENDENT, attempt synchronous start
        val deviceRef = childrenJobs[name]?.device ?: return
        if (childrenJobs[name]?.config?.lifecycleMode != LifecycleMode.INDEPENDENT) {
            finalizeDeviceStart(name, childrenJobs[name]!!.config, deviceRef)
        }
    }

    /**
     * Changes the [LifecycleMode] for the specified device.
     * The device is removed (stopped), then re-added with the new mode.
     */
    public suspend fun changeLifecycleMode(name: Name, newMode: LifecycleMode) {
        childLock.withLock {
            val old = childrenJobs[name] ?: error("Device $name not found")
            val newConfig = old.config.copy(lifecycleMode = newMode)
            removeDeviceUnlocked(name, waitStop = true)
            val newChild = launchChild(
                name,
                old.device,
                newConfig,
                old.meta,
                reuseBus = if (old.reuseBus) old.messageBus else null
            )
            childrenJobs[name] = newChild
            systemBus.emit(SystemLogMessage("Device $name lifecycle changed to $newMode", sourceDevice = name))
        }
        // Possibly start if needed
        val deviceRef = childrenJobs[name]?.device ?: return
        if (newMode != LifecycleMode.INDEPENDENT) {
            finalizeDeviceStart(name, childrenJobs[name]!!.config, deviceRef)
        }
    }

    /**
     * Replaces a device ("hot swap") under the same [name], optionally reusing the old message bus.
     *
     * @param name The device name to replace.
     * @param newDevice The new [Device].
     * @param config The [DeviceLifecycleConfig].
     * @param meta Optional [Meta].
     * @param reuseMessageBus If `true`, keep the child's existing message bus for continuous streaming.
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
        // Possibly start the new device if mode != INDEPENDENT
        val deviceRef = childrenJobs[name]?.device ?: return
        if (config.lifecycleMode != LifecycleMode.INDEPENDENT) {
            finalizeDeviceStart(name, config, deviceRef)
        }
    }

    /**
     * Internal function to remove (and optionally wait-stop) a device by [name].
     * This method must be called from within [childLock].
     *
     * @param waitStop If true, waits for stopping within [DeviceLifecycleConfig.stopTimeout].
     */
    private suspend fun removeDeviceUnlocked(name: Name, waitStop: Boolean) {
        val child = childrenJobs[name] ?: return
        childrenJobs.remove(name)
        restartAttemptsMap.remove(name)

        deviceChanges.emit(DeviceStateEvent.DeviceRemoved(name))
        systemBus.emit(SystemLogMessage("Device $name removed (waitStop=$waitStop)", sourceDevice = name))

        if (waitStop) {
            performStop(child)
        } else {
            CoroutineScope(parentJob + dispatcher).launch {
                performStop(child)
            }
        }
    }

    /**
     * Performs the actual stopping sequence:
     * 1) Attempt `device.stop()` with [stopTimeout]
     * 2) Cancel and join the collector job
     */
    private suspend fun performStop(child: ChildJob) {
        val timeout = child.config.stopTimeout ?: Duration.INFINITE
        val deviceName = child.device.id.parseAsName()
        val result = withTimeoutOrNull(timeout) {
            child.device.stop()
        }
        if (result == null) {
            onStopTimeout(deviceName, child.config)
        }
        // Finally, cancel the collector job
        child.collectorJob.cancelAndJoin()
    }

    /**
     * Returns the child's dedicated message bus if present.
     */
    public fun getChildMessageBus(name: Name): MutableSharedFlow<DeviceMessage>? = childrenJobs[name]?.messageBus

    /**
     * Called after a child device is physically stopped and removed in [removeJobFromRegistry].
     * Override in a subclass if you need additional logic.
     */
    internal open fun onChildStop() {}

    /**
     * Starts multiple devices in a transactional manner:
     * If any one device fails, previously started devices are rolled back (stopped).
     *
     * @param deviceNames The list of devices to start.
     * @return `true` if all devices started successfully, `false` otherwise.
     */
    public suspend fun startDevicesBatch(deviceNames: List<Name>): Boolean {
        val startedSuccessfully = mutableListOf<Name>()
        try {
            for (dn in deviceNames) {
                val job = childrenJobs[dn] ?: continue
                if (job.device.lifecycleState == LifecycleState.INITIAL ||
                    job.device.lifecycleState == LifecycleState.STOPPED) {
                    job.device.start()
                    deviceChanges.emit(DeviceStateEvent.DeviceStarted(dn))
                    startedSuccessfully += dn
                }
            }
            return true
        } catch (ex: Exception) {
            context.logger.error(ex) { "Failed to start device batch. Rolling back." }
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
     * Stops multiple devices in a transactional manner:
     * If any one device fails to stop, tries to rollback (start) previously stopped devices.
     *
     * @param deviceNames The list of devices to stop.
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
        } catch (ex: Exception) {
            context.logger.error(ex) { "Failed to stop device batch. Rolling back." }
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
     * Optionally set up a distributed transport or message broker for the managed devices.
     * By default, this is a stub that logs an informational message.
     */
    public open fun installDistributedTransport() {
        context.logger.info { "installDistributedTransport: implement or override for custom broker." }
    }

    /**
     * An optional utility method that can iterate over all children and call [checkHealth].
     * You might schedule or call it periodically.
     */
    public suspend fun runHealthChecks() {
        childLock.withLock {
            for ((name, child) in childrenJobs) {
                checkHealth(child)
            }
        }
    }
}

/**
 * A default manager implementation with typical flows for messages, system logs, and device changes.
 *
 * @param context The parent context.
 * @param dispatcher The coroutine dispatcher.
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
 * A composite control component is a device that can host child devices.
 */
public interface CompositeControlComponent : Device {
    /**
     * The message bus for device-level messages (often aggregated from children).
     */
    public val messageBus: SharedFlow<DeviceMessage>

    /**
     * A map of child devices keyed by their [Name].
     */
    public val devices: Map<Name, Device>
}

/**
 * A device that supports a composite structure of child components using [spec].
 *
 * @param D Self type for the device.
 * @param spec The [CompositeControlComponentSpec] describing properties/actions/children.
 * @param context The parent [Context].
 * @param meta The device's metadata.
 * @param config The [DeviceLifecycleConfig] for this device.
 * @param registry Optionally specify a [ComponentRegistry] (defaults to [context.componentRegistry]).
 * @param hubManager A custom [AbstractDeviceHubManager], or a default instance if not provided.
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

    /**
     * A shared flow for messages. By default, delegates to [hubManager.messageBus].
     * Override if you need a separate bus.
     */
    final override val messageBus: MutableSharedFlow<DeviceMessage>
        get() = hubManager.messageBus

    override fun toString(): String = "Device(id=$id, spec=$spec)"

    override val devices: Map<Name, Device>
        get() = hubManager.devices

    private val childConfigs: List<ChildComponentConfig<*>> = spec.childSpecs.values.toList()

    init {
        // Optionally, we can handle action execution here if needed
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
     * Instantiates (and synchronously adds) all child devices declared in [spec.childSpecs].
     * If a child's mode is [LifecycleMode.LINKED], it will attempt to start the device here.
     * If [LifecycleMode.INDEPENDENT], it is added but not started automatically.
     * If [LifecycleMode.LAZY], it is added but not started unless requested later.
     */
    public suspend fun initChildren() {
        for (childCfg in childConfigs) {
            val childSpec = childCfg.spec
            val childDevice: ConfigurableCompositeControlComponent<*> = if (childSpec is DeviceSpecification<*>) {
                childSpec.deviceFactory(context, childCfg.meta ?: Meta.EMPTY)
            } else {
                ConfigurableCompositeControlComponent(
                    childSpec,
                    context,
                    childCfg.meta ?: Meta.EMPTY,
                    childCfg.config,
                    effectiveRegistry
                )
            }
            // Synchronous add; also will start if not INDEPENDENT
            hubManager.addDeviceSync(childCfg.name, childDevice, childCfg.config, childCfg.meta)
        }
    }

    /**
     * Called when this device is starting.
     * Default logic: call [spec.onOpen], validate the device,
     * then automatically start child devices if they are [LifecycleMode.LINKED].
     */
    override suspend fun onStart() {
        with(spec) {
            self.onOpen()
            validate(self)
        }
        // Start child devices if not LAZY or already started
        hubManager.devices.values
            .filter { it.lifecycleState == LifecycleState.INITIAL }
            .forEach { child ->
                val mode = hubManager.childrenJobs[child.id.parseAsName()]?.lifecycleMode
                if (mode != LifecycleMode.LAZY) {
                    child.start()
                }
            }
    }

    /**
     * Called when this device is stopping.
     * Default logic: attempt to stop each child device (if started), then call [spec.onClose].
     */
    override suspend fun onStop() {
        // For each child that is started, try to stop them gracefully
        hubManager.devices.values.forEach { child ->
            if (child.lifecycleState == LifecycleState.STARTED) {
                // We do this in parallel, but might consider sequential in certain scenarios
                launch(child.coroutineContext) {
                    val stopTimeout = hubManager.childrenJobs[child.id.parseAsName()]?.config?.stopTimeout ?: Duration.INFINITE
                    val stopped = withTimeoutOrNull(stopTimeout) {
                        child.stop()
                    }
                    if (stopped == null) {
                        // If timed out, let manager handle
                        val job = hubManager.childrenJobs[child.id.parseAsName()]
                        if (job != null) {
                            hubManager.onStopTimeout(child.id.parseAsName(), job.config)
                        }
                    }
                }
            }
        }
        with(spec) {
            self.onClose()
        }
    }

    /**
     * Called after a child device stops within the manager's logic.
     * By default, does nothing.
     */
    internal open fun onChildStop() {
        // no-op
    }

    /**
     * Retrieves a child device by [name].
     *
     * @throws IllegalStateException if not found or type mismatch.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <CD : ConfigurableCompositeControlComponent<CD>> getChildDevice(name: Name): CD {
        return hubManager.devices[name] as? CD
            ?: error("Child device $name not found or type mismatch.")
    }

    /**
     * Gets the child's message bus if you need direct access, or `null` if not found.
     */
    public fun getChildMessageBus(name: Name): SharedFlow<DeviceMessage>? = hubManager.getChildMessageBus(name)

    /**
     * A property delegate to get a child device by [name] or by property name if [name] is null.
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
     * Operator to get a child device by string name.
     */
    public inline operator fun <reified Dev : Device> get(name: String): Dev? = this[name.asName()]
}

/**
 * Stops the device with a given [timeout].
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

/**
 * A simple base class for specifying a [ConfigurableCompositeControlComponent].
 *
 * @param D The type of device.
 * @param deviceFactory A factory function that creates a device given a [Context] and [Meta].
 */
public abstract class DeviceSpecification<D : ConfigurableCompositeControlComponent<D>>(
    public val deviceFactory: (Context, Meta) -> D
) : CompositeControlComponentSpec<D>()
