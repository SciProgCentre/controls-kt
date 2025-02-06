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
 * Extension function to safely get the completed value of a [Deferred] or return `null`.
 *
 * @param T The type parameter of the deferred result.
 * @return The completed value if available and not cancelled, or `null` otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Deferred<T>.getCompletedOrNull(): T? =
    if (isCompleted && !isCancelled) getCompleted() else null

/**
 * EventBus interface for publishing and subscribing to application-level events.
 */
public interface EventBus {
    /** A shared flow that emits events across the application. */
    public val events: SharedFlow<Any>
    /**
     * Publishes an event to the event bus.
     *
     * @param event The event to publish.
     */
    public suspend fun publish(event: Any)
}

/**
 * Default implementation of [EventBus] using a [MutableSharedFlow].
 */
public class DefaultEventBus(
    replay: Int = 100,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST
) : EventBus {
    private val _events = MutableSharedFlow<Any>(replay = replay, onBufferOverflow = onBufferOverflow)
    override val events: SharedFlow<Any> get() = _events

    override suspend fun publish(event: Any) {
        _events.emit(event)
    }
}

/**
 * TransportAdapter interface for distributed communications.
 * This abstraction allows plugging in different transport mechanisms.
 */
public interface TransportAdapter {
    /**
     * Sends a [DeviceMessage] over the transport.
     *
     * @param message The device message to send.
     */
    public suspend fun send(message: DeviceMessage)

    /**
     * Subscribes to incoming device messages.
     *
     * @return A [Flow] of [DeviceMessage].
     */
    public fun subscribe(): Flow<DeviceMessage>
}

/**
 * Default stub implementation of [TransportAdapter] for in-process communication.
 */
public class DefaultTransportAdapter(
    private val eventBus: EventBus,
    private val logger: Logger = DefaultLogManager(),
) : TransportAdapter {
    private val _messages = MutableSharedFlow<DeviceMessage>(replay = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override suspend fun send(message: DeviceMessage) {
        _messages.emit(message)
        logger.info { "TransportAdapter: message sent -> ${message.sourceDevice}" }
        eventBus.publish("Message sent: ${message.sourceDevice}")
    }
    override fun subscribe(): Flow<DeviceMessage> = _messages.asSharedFlow()
}

/**
 * TransactionManager interface for executing a block of operations.
 */
public interface TransactionManager {
    /**
     * Executes [block] within a transaction. If an exception occurs, a rollback is performed.
     *
     * @param block The suspend function representing the transactional block.
     * @return The result of the block execution.
     */
    public suspend fun <T> withTransaction(block: suspend () -> T): T
}

/**
 * Default implementation of [TransactionManager].
 * This implementation wraps the block in a try/catch and publishes transaction events.
 */
public class DefaultTransactionManager(
    private val eventBus: EventBus,
    private val logger: Logger = DefaultLogManager()
) : TransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        eventBus.publish(TransactionEvent.TransactionStarted)
        return try {
            val result = block()
            eventBus.publish(TransactionEvent.TransactionCommitted)
            result
        } catch (ex: Exception) {
            logger.error(ex) { "Transaction failed, rolling back." }
            eventBus.publish(TransactionEvent.TransactionRolledBack)
            throw ex
        }
    }
}

/**
 * Transaction events used by [DefaultTransactionManager].
 */
public sealed class TransactionEvent {
    public object TransactionStarted : TransactionEvent()
    public object TransactionCommitted : TransactionEvent()
    public object TransactionRolledBack : TransactionEvent()
}

/**
 * Interface for publishing metrics.
 */
public interface MetricPublisher {
    /**
     * Publishes a metric with the given parameters.
     *
     * @param name The name of the metric.
     * @param value The numeric value of the metric.
     * @param tags Optional tags associated with the metric.
     */
    public fun publishMetric(name: String, value: Double, tags: Map<String, String> = emptyMap())
}

/**
 * Default stub implementation of [MetricPublisher] which logs metrics.
 */
public class DefaultMetricPublisher(
    private val logger: Logger = DefaultLogManager()
) : MetricPublisher {
    override fun publishMetric(name: String, value: Double, tags: Map<String, String>) {
        logger.info { "Metric published: $name = $value, tags: $tags" }
    }
}

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
 * Defines how the manager should initiate start of a device when [attachDevice] is called.
 */
public enum class StartMode {
    /**
     * Do not start the device at all; just attach/register it in the manager.
     */
    NONE,

    /**
     * Start the device asynchronously (do not wait for completion of [device.start()]).
     * If [DeviceLifecycleConfig.lifecycleMode] = INDEPENDENT, no start will be performed anyway.
     */
    ASYNC,

    /**
     * Start the device synchronously, waiting up to [DeviceLifecycleConfig.startTimeout].
     * If [DeviceLifecycleConfig.lifecycleMode] = INDEPENDENT, no auto-start is performed.
     */
    SYNC
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
 * Returns `true` if the device is healthy, `false` otherwise.
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
 * Represents various events or state changes for devices managed by an [AbstractDeviceHubManager].
 */
public sealed class DeviceStateEvent {
    public abstract val deviceName: Name

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
 * @param lifecycleMode The [LifecycleMode] of the device.
 * @param messageBuffer The buffer size for the child's message flow.
 * @param startDelay An additional delay before starting the device.
 * @param startTimeout Timeout for starting the device.
 * @param stopTimeout Timeout for stopping the device.
 * @param coroutineScope An optional [CoroutineScope] in which this device runs.
 * @param dispatcher An optional [CoroutineDispatcher] for concurrency.
 * @param onError The [ChildDeviceErrorHandler] strategy.
 * @param healthChecker An optional [HealthChecker].
 * @param restartPolicy The [RestartPolicy] used if [onError] is [ChildDeviceErrorHandler.RESTART].
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
     *
     * @param builder The [DeviceLifecycleConfigBuilder] to configure.
     * @param deviceName The name of the device.
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
     * Loads and applies external configuration via [externalApplier].
     *
     * @param deviceName The name of the device.
     * @param externalApplier The external configuration applier.
     */
    public suspend fun applyExternalConfig(deviceName: Name, externalApplier: ExternalConfigApplier) {
        externalApplier.applyConfig(this, deviceName)
    }

    /**
     * Builds the resulting [DeviceLifecycleConfig].
     *
     * @return The constructed [DeviceLifecycleConfig].
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
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.LINKED].
 */
public fun DeviceLifecycleConfigBuilder.linked() {
    lifecycleMode = LifecycleMode.LINKED
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.INDEPENDENT].
 */
public fun DeviceLifecycleConfigBuilder.independent() {
    lifecycleMode = LifecycleMode.INDEPENDENT
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.lifecycleMode] to [LifecycleMode.LAZY].
 */
public fun DeviceLifecycleConfigBuilder.lazy() {
    lifecycleMode = LifecycleMode.LAZY
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.onError] to [ChildDeviceErrorHandler.RESTART].
 */
public fun DeviceLifecycleConfigBuilder.restartOnError() {
    onError = ChildDeviceErrorHandler.RESTART
}

/**
 * Shortcut to set [DeviceLifecycleConfigBuilder.onError] to [ChildDeviceErrorHandler.PROPAGATE].
 */
public fun DeviceLifecycleConfigBuilder.propagateError() {
    onError = ChildDeviceErrorHandler.PROPAGATE
}

/**
 * Sets both [DeviceLifecycleConfigBuilder.startTimeout] and [DeviceLifecycleConfigBuilder.stopTimeout]
 * to the provided [timeout].
 *
 * @param timeout The timeout value to be used.
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
     * Returns `null` if not found or if the class cast fails.
     *
     * @param name The name of the specification.
     * @return The [CompositeControlComponentSpec] or `null` if unavailable.
     */
    public fun <D : ConfigurableCompositeControlComponent<D>> getSpec(name: Name): CompositeControlComponentSpec<D>?
}

/**
 * A default plugin-based manager for specifications.
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
     *
     * @param spec The specification to register.
     * @param name The name under which to register the spec.
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
 * Convenience function for [ContextBuilder]: install a [ComponentRegistryManager] plugin.
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
    /** The specification of the child component. */
    public val spec: CompositeControlComponentSpec<CD>
    /** The lifecycle configuration for the child component. */
    public val config: DeviceLifecycleConfig
    /** Optional metadata for the child component. */
    public val meta: Meta?
    /** The name of the child component. */
    public val name: Name
}

/**
 * Base interface describing a composite device specification.
 * It declares properties and actions, as well as potential child specifications.
 *
 * @param D The type of device using this specification.
 */
public interface CompositeDeviceSpec<D : ConfigurableCompositeControlComponent<D>> {
    /** Map of property specifications. */
    public val properties: Map<String, DevicePropertySpec<D, *>>
    /** Map of action specifications. */
    public val actions: Map<String, DeviceActionSpec<D, *, *>>
    /** Map of child component configurations. */
    public val childSpecs: Map<String, ChildComponentConfig<*>>

    /**
     * Called when the device is opening (starting).
     *
     * @receiver The device instance.
     */
    public suspend fun D.onOpen()

    /**
     * Called when the device is closing (stopping).
     *
     * @receiver The device instance.
     */
    public suspend fun D.onClose()

    /**
     * Validates the [device]'s state or properties. Throws an exception if validation fails.
     *
     * @param device The device instance to validate.
     */
    public fun validate(device: D)

    /**
     * Registers a [deviceProperty].
     *
     * @param deviceProperty The property specification to register.
     * @return The registered property specification.
     */
    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P

    /**
     * Registers a [deviceAction].
     *
     * @param deviceAction The action specification to register.
     * @return The registered action specification.
     */
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    /**
     * Creates a [PropertyDescriptor] internally.
     *
     * @param propertyName The name of the property.
     * @param converter The meta converter for the property.
     * @param mutable Indicates if the property is mutable.
     * @param property The [KProperty] reference.
     * @param descriptorBuilder A builder function to further configure the descriptor.
     * @return The created [PropertyDescriptor].
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
     *
     * @param actionName The name of the action.
     * @param inputConverter The meta converter for the input.
     * @param outputConverter The meta converter for the output.
     * @param property The [KProperty] reference.
     * @param descriptorBuilder A builder function to further configure the descriptor.
     * @return The created [ActionDescriptor].
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
     *
     * @param converter The meta converter for the property.
     * @param descriptorBuilder Optional builder for the property descriptor.
     * @param name An optional name override.
     * @param read A suspend function to read the property value.
     * @return A property delegate provider for the declared property.
     */
    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>>

    /**
     * Declares a mutable property with the given [converter].
     *
     * @param converter The meta converter for the property.
     * @param descriptorBuilder Optional builder for the property descriptor.
     * @param name An optional name override.
     * @param read A suspend function to read the property value.
     * @param write A suspend function to write the property value.
     * @return A property delegate provider for the declared mutable property.
     */
    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>>

    /**
     * Declares an action with the specified input and output converters and execution block.
     *
     * @param inputConverter The meta converter for the action input.
     * @param outputConverter The meta converter for the action output.
     * @param descriptorBuilder Optional builder for the action descriptor.
     * @param name An optional name override.
     * @param execute The suspend function to execute the action.
     * @return A property delegate provider for the declared action.
     */
    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>>
}

/**
 * Default implementation of [CompositeDeviceSpec].
 *
 * @param D The type of device.
 * @param registry (Optional) a [ComponentRegistry] to lookup child specifications by name.
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
        // Default implementation is no-op.
    }

    override suspend fun D.onClose() {
        // Default implementation is no-op.
    }

    override fun validate(device: D) {
        // Verify that all declared properties and actions are registered in the device.
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
        // Prevent duplicate registration.
        check(propertyMap[deviceProperty.name] == null) { "Property ${deviceProperty.name} is already registered." }
        propertyMap[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    override fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
        // Prevent duplicate registration.
        check(actionMap[deviceAction.name] == null) { "Action ${deviceAction.name} is already registered." }
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
     * A convenience method for declaring a child specification.
     * This method references either a fallback spec or attempts to retrieve one from the [registry] by [specKeyInRegistry].
     *
     * @param fallbackSpec The spec to use if not found in the registry.
     * @param specKeyInRegistry The name key in the registry, if any.
     * @param childDeviceName The actual name of the child device (defaults to the property name).
     * @param metaBuilder A lambda to build [Meta] for the child.
     * @param configBuilder A lambda to build the [DeviceLifecycleConfig] for the child.
     * @return A property delegate provider for the child specification.
     */
    public fun <CDS : CompositeControlComponentSpec<CD>, CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        fallbackSpec: CDS,
        specKeyInRegistry: Name? = null,
        childDeviceName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {}
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
                    try {
                        withContext(device.coroutineContext) { device.execute(input) }
                    } catch (ex: Exception) {
                        device.logger.error(ex) { "Error executing action $actionName on device ${device.id}" }
                        throw ex
                    }
            })
            ReadOnlyProperty { _, _ -> devAction }
        }
}

/**
 * Declares an action with a `Unit` input and a `Unit` output.
 *
 * @param descriptorBuilder Optional lambda to configure the action's metadata.
 * @param name Optionally override the action's name (defaults to the property name).
 * @param execute A suspend function that is called with no input and produces no output.
 * @return A property delegate provider for the declared action.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.unitAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.() -> Unit
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    action(MetaConverter.unit, MetaConverter.unit, descriptorBuilder, name) { execute() }

/**
 * Declares an action with a [Meta] input and a [Meta] output.
 *
 * @param descriptorBuilder Optional lambda to configure the action's metadata.
 * @param name Optionally override the action's name (defaults to the property name).
 * @param execute A suspend function that takes a [Meta] input and returns a [Meta] output.
 * @return A property delegate provider for the declared action.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(Meta) -> Meta
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    action(MetaConverter.meta, MetaConverter.meta, descriptorBuilder, name) { execute(it) }

/**
 * An abstract manager for devices, handling lifecycle, error policies, transactions, distributed transport,
 * structured concurrency, and event/metric publishing.
 *
 * This class uses a global exception handler and a [SupervisorJob] for centralized error handling.
 * All coroutines are launched with a combined context of the parent job, dispatcher, and global exception handler.
 *
 * @param context The [Context] for logging and plugin management.
 * @param dispatcher A [CoroutineDispatcher] for concurrency; default is [Dispatchers.Default].
 */
public abstract class AbstractDeviceHubManager(
    public val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Global exception handler for all coroutines in this manager.
     */
    protected val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, ex ->
        context.logger.error(ex) { "Unhandled exception in global scope (DeviceHubManager)" }
    }

    /**
     * SupervisorJob ensures that child coroutines are isolated.
     */
    protected val parentJob: Job = SupervisorJob()

    /**
     * A mutex to protect access to [childrenJobs].
     */
    protected val childLock: Mutex = Mutex()

    /**
     * Internal map that keeps track of each child's [ChildJob].
     */
    internal val childrenJobs: MutableMap<Name, ChildJob> = mutableMapOf()

    /**
     * Snapshot of current devices (not guaranteed to be consistent without a lock).
     * For a safe snapshot, use [getDevicesSafe].
     */
    public val devices: Map<Name, Device>
        get() = childrenJobs.mapValues { it.value.device }

    /**
     * Returns a safe snapshot of current devices by locking [childLock].
     *
     * @return A [Map] of device names to devices.
     */
    public suspend fun getDevicesSafe(): Map<Name, Device> = childLock.withLock {
        childrenJobs.mapValues { it.value.device }
    }

    /**
     * A [MutableSharedFlow] for broadcasting (or replaying) [DeviceMessage]s from all devices.
     */
    public abstract val messageBus: MutableSharedFlow<DeviceMessage>

    /**
     * A [MutableSharedFlow] for system-level or log messages.
     */
    public abstract val systemBus: MutableSharedFlow<SystemLogMessage>

    /**
     * A [MutableSharedFlow] for [DeviceStateEvent] changes (e.g., device added/removed/failed).
     */
    public abstract val deviceChanges: MutableSharedFlow<DeviceStateEvent>

    /**
     * Additional [EventBus] for application-level events.
     */
    public abstract val eventBus: EventBus

    /**
     * Metric publisher for logging and monitoring.
     */
    public open val metricPublisher: MetricPublisher = DefaultMetricPublisher(context.logger)

    /**
     * Transaction manager for wrapping critical operations.
     */
    public abstract val transactionManager: TransactionManager

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
     * Represents a running child device along with its job, configuration, and flows.
     *
     * @property device The managed device instance.
     * @property collectorJob The coroutine job collecting messages from [device.messageFlow].
     * @property config The lifecycle configuration for this device.
     * @property messageBus A dedicated message bus for this child.
     * @property systemBus The shared system-level bus.
     * @property meta Optional metadata for the device.
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
     * Global function for launching coroutines with the combined context.
     *
     * @param block The suspend function to execute.
     * @return The launched [Job].
     */
    internal fun launchGlobal(block: suspend CoroutineScope.() -> Unit): Job =
        CoroutineScope(parentJob + dispatcher + exceptionHandler).launch { block() }

    /**
     * Called when an error is thrown from a child's coroutine.
     *
     * @param ex The thrown exception.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration of the child device.
     */
    protected open suspend fun onChildErrorCaught(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Error in child device $childName with policy ${config.onError}" }
    }

    /**
     * Called if a child error triggers [ChildDeviceErrorHandler.STOP_PARENT], indicating the parent must stop.
     *
     * The default implementation cancels the [parentJob].
     *
     * @param ex The exception that caused the stop.
     * @param childName The name of the child device.
     */
    protected open suspend fun onParentStopRequested(ex: Throwable, childName: Name) {
        context.logger.error(ex) { "Stopping parent due to error in child $childName" }
        parentJob.cancelAndJoin()
    }

    /**
     * Called if [ChildDeviceErrorHandler.CUSTOM] is used.
     * Override to implement a custom strategy for error handling.
     *
     * @param ex The exception that occurred.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration for the child device.
     */
    protected open suspend fun onCustomError(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        context.logger.error(ex) { "Custom error strategy for device $childName: override onCustomError if needed." }
    }

    /**
     * Called when a device times out while starting.
     * The default implementation throws a runtime exception.
     *
     * @param deviceName The name of the device.
     * @param config The lifecycle configuration for the device.
     */
    protected open suspend fun onStartTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        val msg = "Timeout while starting $deviceName."
        context.logger.error { msg }
        throw RuntimeException(msg)
    }

    /**
     * Called when a device times out while stopping.
     * The default implementation logs a warning.
     *
     * @param deviceName The name of the device.
     * @param config The lifecycle configuration for the device.
     */
    internal open suspend fun onStopTimeout(deviceName: Name, config: DeviceLifecycleConfig) {
        context.logger.warn { "Timeout while stopping $deviceName. You may override onStopTimeout if needed." }
    }

    /**
     * Performs a health check on the given child device.
     *
     * If the [DeviceLifecycleConfig.healthChecker] is present and returns false,
     * a [DeviceStateEvent.DeviceFailed] event is emitted.
     *
     * @param child The [ChildJob] representing the device.
     */
    internal open suspend fun checkHealth(child: ChildJob) {
        val hc = child.config.healthChecker ?: return
        if (!hc.isHealthy(child.device)) {
            val ex = RuntimeException("Health check failed for device ${child.device.id}")
            deviceChanges.emit(DeviceStateEvent.DeviceFailed(child.device.id.parseAsName(), ex))
        }
    }

    /**
     * Launches a coroutine to collect messages from [device.messageFlow].
     *
     * This method does not start the device automatically; it only sets up the collector.
     *
     * @param name The name of the device.
     * @param device The device instance.
     * @param config The lifecycle configuration for the device.
     * @param meta Optional metadata.
     * @param reuseBus If not null, reuses the provided message bus (e.g., for hot-swap).
     * @return A [ChildJob] representing the running child device.
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
        val childScope = config.coroutineScope ?: CoroutineScope(parentJob + dispatcher + exceptionHandler)

        val collectorJob = childScope.launch(CoroutineName("Collect device $name")) {
            try {
                device.messageFlow.collect { msg ->
                    val wrapped = msg.changeSource { name.plus(it) }
                    childMessageBus.emit(wrapped)
                    messageBus.emit(wrapped)
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
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
     * Handles the error policy defined in [DeviceLifecycleConfig.onError] for a given child device.
     *
     * @param ex The exception that occurred.
     * @param childName The name of the child device.
     * @param config The lifecycle configuration for the child device.
     */
    private suspend fun handleErrorPolicy(ex: Throwable, childName: Name, config: DeviceLifecycleConfig) {
        when (config.onError) {
            ChildDeviceErrorHandler.IGNORE -> { /* do nothing */ }
            ChildDeviceErrorHandler.RESTART -> {
                context.logger.info { "Scheduling restart for $childName due to error: ${ex.message}" }
                scheduleRestart(childName, config.restartPolicy)
            }
            ChildDeviceErrorHandler.STOP_PARENT -> onParentStopRequested(ex, childName)
            ChildDeviceErrorHandler.PROPAGATE -> throw ex
            ChildDeviceErrorHandler.CUSTOM -> onCustomError(ex, childName, config)
        }
    }

    /**
     * Schedules a restart for the device identified by [childName] according to the given [policy].
     *
     * @param childName The name of the device to restart.
     * @param policy The [RestartPolicy] to use for scheduling.
     */
    private suspend fun scheduleRestart(childName: Name, policy: RestartPolicy) {
        childLock.withLock {
            if (childName in restartingDevices) return
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
                context.logger.info { "Delaying restart of $childName by $delayDuration (attempt $attempts)" }
                delay(delayDuration)
            }
            restartDevice(childName)
        } finally {
            childLock.withLock { restartingDevices.remove(childName) }
        }
    }

    /**
     * Calculates the delay based on [RestartPolicy].
     */
    private fun calculateDelay(policy: RestartPolicy, attempts: Int): Duration {
        return when (policy.strategy) {
            RestartStrategy.LINEAR -> policy.delayBetweenAttempts
            RestartStrategy.EXPONENTIAL_BACKOFF -> policy.delayBetweenAttempts * 2.0.pow((attempts - 1).toDouble())
            RestartStrategy.CUSTOM -> Duration.ZERO // Custom strategy can be overridden.
        }
    }

    /**
     * Removes the child job from [childrenJobs] if it matches the provided [device], and emits a [DeviceStateEvent.DeviceDetached] event.
     * Also resets the replay cache for the child message bus.
     *
     * @param name The name of the device.
     * @param device The device instance.
     * @param bus The child's message bus.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun removeJobFromRegistry(
        name: Name,
        device: Device,
        bus: MutableSharedFlow<DeviceMessage>
    ) {
        val shouldRemove = childLock.withLock {
            val current = childrenJobs[name]
            if (current?.device == device) {
                childrenJobs.remove(name)
                restartAttemptsMap.remove(name)
                true
            } else false
        }
        if (shouldRemove) {
            bus.resetReplayCache()
            deviceChanges.emit(DeviceStateEvent.DeviceDetached(name))
            systemBus.emit(SystemLogMessage("Device $name physically removed.", sourceDevice = name))
            if (device is ConfigurableCompositeControlComponent<*>) {
                device.onChildStop()
            }
        }
    }

    /**
     * Attaches (registers) a device in the manager under the given [name], using the provided [config] and optional [meta].
     *
     * If [startMode] is [StartMode.NONE], the device is only attached.
     * If [startMode] is [StartMode.ASYNC] or [StartMode.SYNC], the device is started (unless its lifecycle mode is [LifecycleMode.INDEPENDENT]).
     * If an existing device is present under [name] and differs from [device], it is removed first.
     *
     * @param name The unique name of the device.
     * @param device The [Device] instance to attach.
     * @param config The lifecycle configuration for the device.
     * @param meta Optional metadata for the device.
     * @param startMode Determines whether to auto-start the device.
     */
    public suspend fun attachDevice(
        name: Name,
        device: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        startMode: StartMode = StartMode.NONE
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
        systemBus.emit(SystemLogMessage("Device $name attached, startMode=$startMode", sourceDevice = name))
        metricPublisher.publishMetric("device.attach", 1.0, mapOf("device" to name.toString()))
        if (config.lifecycleMode == LifecycleMode.INDEPENDENT) return
        when (startMode) {
            StartMode.NONE -> Unit
            StartMode.ASYNC -> launchGlobal { doStartDevice(name, config, device) }
            StartMode.SYNC -> doStartDevice(name, config, device)
        }
    }

    /**
     * Internal helper that starts a device while respecting [startDelay] and [startTimeout].
     *
     * @param name The device name.
     * @param config The lifecycle configuration.
     * @param device The device instance.
     */
    internal open suspend fun doStartDevice(name: Name, config: DeviceLifecycleConfig, device: Device) {
        if (config.startDelay > Duration.ZERO) delay(config.startDelay)
        val startTimeout = config.startTimeout ?: Duration.INFINITE
        val success = withTimeoutOrNull(startTimeout) {
            device.start()
        }
        if (success == null) {
            onStartTimeout(name, config)
        } else {
            deviceChanges.emit(DeviceStateEvent.DeviceStarted(name))
            metricPublisher.publishMetric("device.start", 1.0, mapOf("device" to name.toString()))
            if (config.restartPolicy.resetOnSuccess) restartAttemptsMap[name] = 0
        }
    }

    /**
     * Detaches (removes) a device from the manager by its [name].
     * If [waitStop] is true, waits until the device has fully stopped.
     *
     * @param name The unique name of the device.
     * @param waitStop If true, waits for the device to stop.
     */
    public suspend fun detachDevice(name: Name, waitStop: Boolean = false) {
        val child = childLock.withLock {
            childrenJobs.remove(name)?.also {
                restartAttemptsMap.remove(name)
            }
        }
        if (child != null) {
            deviceChanges.emit(DeviceStateEvent.DeviceRemoved(name))
            systemBus.emit(SystemLogMessage("Device $name removed (waitStop=$waitStop)", sourceDevice = name))
            metricPublisher.publishMetric("device.detach", 1.0, mapOf("device" to name.toString()))
            if (waitStop) {
                performStop(child)
            } else {
                launchGlobal { performStop(child) }
            }
        }
    }

    /**
     * Helper method called to start a device after attachment.
     *
     * @param name The device name.
     * @param config The lifecycle configuration.
     * @param device The device instance.
     */
    private suspend fun finalizeDeviceStart(name: Name, config: DeviceLifecycleConfig, device: Device) {
        if (config.startDelay > Duration.ZERO) delay(config.startDelay)
        val startTimeout = config.startTimeout ?: Duration.INFINITE
        val success = withTimeoutOrNull(startTimeout) { device.start() }
        if (success == null) {
            onStartTimeout(name, config)
        } else {
            deviceChanges.emit(DeviceStateEvent.DeviceStarted(name))
            metricPublisher.publishMetric("device.start", 1.0, mapOf("device" to name.toString()))
            if (config.restartPolicy.resetOnSuccess) restartAttemptsMap[name] = 0
        }
    }

    /**
     * Restarts a device, preserving its [DeviceLifecycleConfig] and [Meta].
     * This method stops the device (if running) and relaunches it.
     *
     * @param name The unique name of the device to restart.
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
            metricPublisher.publishMetric("device.restart", 1.0, mapOf("device" to name.toString()))
        }
        val deviceRef = childrenJobs[name]?.device ?: return
        if (childrenJobs[name]?.config?.lifecycleMode != LifecycleMode.INDEPENDENT) {
            finalizeDeviceStart(name, childrenJobs[name]!!.config, deviceRef)
        }
    }

    /**
     * Changes the [LifecycleMode] for the specified device.
     * The device is stopped and then re-attached with the new mode.
     *
     * @param name The unique name of the device.
     * @param newMode The new lifecycle mode.
     */
    public suspend fun changeLifecycleMode(name: Name, newMode: LifecycleMode) {
        val old = childLock.withLock {
            val existing = childrenJobs[name] ?: error("Device $name not found")
            val newConfig = existing.config.copy(lifecycleMode = newMode)
            childrenJobs.remove(name)
            restartAttemptsMap.remove(name)
            Triple(existing.device, newConfig, existing.meta)
        }
        val newChild = launchChild(
            name,
            old.first,
            old.second,
            old.third,
            reuseBus = null
        )
        childLock.withLock {
            childrenJobs[name] = newChild
        }
        systemBus.emit(SystemLogMessage("Device $name lifecycle changed to $newMode", sourceDevice = name))
        metricPublisher.publishMetric("device.lifecycle.change", 1.0, mapOf("device" to name.toString(), "newMode" to newMode.name))
        val deviceRef = childrenJobs[name]?.device ?: return
        if (newMode != LifecycleMode.INDEPENDENT) {
            finalizeDeviceStart(name, childrenJobs[name]!!.config, deviceRef)
        }
    }

    /**
     * Replaces a device ("hot swap") under the same [name], optionally reusing the old message bus.
     *
     * @param name The unique name of the device to replace.
     * @param newDevice The new [Device] instance.
     * @param config The new lifecycle configuration.
     * @param meta Optional metadata.
     * @param reuseMessageBus If true, the existing message bus is reused.
     */
    public suspend fun hotSwapDevice(
        name: Name,
        newDevice: Device,
        config: DeviceLifecycleConfig,
        meta: Meta? = null,
        reuseMessageBus: Boolean = false
    ) {
        transactionManager.withTransaction {
            val oldBus = childLock.withLock { childrenJobs[name]?.messageBus }
            removeDeviceUnlocked(name, waitStop = true)
            childLock.withLock {
                val newChild = launchChild(name, newDevice, config, meta, oldBus.takeIf { reuseMessageBus })
                childrenJobs[name] = newChild
                systemBus.emit(SystemLogMessage("Device $name hot-swapped", sourceDevice = name))
                metricPublisher.publishMetric("device.hotswap", 1.0, mapOf("device" to name.toString()))
            }
            val deviceRef = childLock.withLock { childrenJobs[name]?.device }
            if (deviceRef != null && config.lifecycleMode != LifecycleMode.INDEPENDENT) {
                finalizeDeviceStart(name, config, deviceRef)
            }
        }
    }

    /**
     * Internal function to remove (and optionally wait-stop) a device by [name].
     *
     * @param waitStop If true, waits for the device to stop within [DeviceLifecycleConfig.stopTimeout].
     */
    private suspend fun removeDeviceUnlocked(name: Name, waitStop: Boolean) {
        val child = childLock.withLock { childrenJobs[name] } ?: return
        childLock.withLock {
            childrenJobs.remove(name)
            restartAttemptsMap.remove(name)
        }
        deviceChanges.emit(DeviceStateEvent.DeviceRemoved(name))
        systemBus.emit(SystemLogMessage("Device $name removed (waitStop=$waitStop)", sourceDevice = name))
        if (waitStop) {
            performStop(child)
        } else {
            launchGlobal { performStop(child) }
        }
    }

    /**
     * Performs the stopping sequence:
     * 1) Attempts to stop the device (with [stopTimeout] if specified).
     * 2) Cancels and joins the collector job.
     *
     * @param child The [ChildJob] representing the device.
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
        withContext(NonCancellable) {
            child.collectorJob.cancelAndJoin()
        }
        metricPublisher.publishMetric("device.stop", 1.0, mapOf("device" to deviceName.toString()))
    }

    /**
     * Returns the dedicated message bus of the child device with the given [name], if present.
     *
     * @param name The unique name of the child device.
     * @return The [MutableSharedFlow] of [DeviceMessage] or `null` if not found.
     */
    public fun getChildMessageBus(name: Name): MutableSharedFlow<DeviceMessage>? = childrenJobs[name]?.messageBus

    /**
     * Called after a child device is physically stopped and removed in [removeJobFromRegistry].
     * Override in a subclass if additional logic is needed.
     */
    internal open fun onChildStop() {}

    /**
     * Starts multiple devices in a transactional manner.
     * If any device fails to start, already started devices are rolled back (stopped).
     *
     * @param deviceNames The list of device names to start.
     * @return `true` if all devices started successfully, `false` otherwise.
     */
    public suspend fun startDevicesBatch(deviceNames: List<Name>): Boolean = coroutineScope {
        val deferredList = deviceNames.mapNotNull { dn ->
            childrenJobs[dn]?.let { job ->
                if (job.config.lifecycleMode != LifecycleMode.LAZY &&
                    (job.device.lifecycleState == LifecycleState.INITIAL || job.device.lifecycleState == LifecycleState.STOPPED)
                ) {
                    async {
                        try {
                            job.device.start()
                            deviceChanges.emit(DeviceStateEvent.DeviceStarted(dn))
                            dn
                        } catch (ex: Exception) {
                            context.logger.error(ex) { "Error starting device $dn in batch" }
                            throw ex
                        }
                    }
                } else null
            }
        }
        try {
            deferredList.awaitAll()
            true
        } catch (ex: Exception) {
            context.logger.error(ex) { "Failed to start device batch. Rolling back." }
            deferredList.mapNotNull { it.getCompletedOrNull() }.forEach { dn ->
                childrenJobs[dn]?.let { job ->
                    try {
                        job.device.stop()
                        deviceChanges.emit(DeviceStateEvent.DeviceStopped(dn))
                    } catch (rollbackEx: Exception) {
                        context.logger.error(rollbackEx) { "Failed to rollback stop for device $dn" }
                    }
                }
            }
            false
        }
    }

    /**
     * Stops multiple devices in a transactional manner.
     * If any device fails to stop, already stopped devices are rolled back (started).
     *
     * @param deviceNames The list of device names to stop.
     * @return `true` if all devices were stopped successfully, `false` otherwise.
     */
    public suspend fun stopDevicesBatch(deviceNames: List<Name>): Boolean = coroutineScope {
        val deferredList = deviceNames.mapNotNull { dn ->
            childrenJobs[dn]?.let { job ->
                if (job.device.lifecycleState == LifecycleState.STARTED) {
                    async {
                        try {
                            job.device.stop()
                            deviceChanges.emit(DeviceStateEvent.DeviceStopped(dn))
                            dn
                        } catch (ex: Exception) {
                            context.logger.error(ex) { "Error stopping device $dn in batch" }
                            throw ex
                        }
                    }
                } else null
            }
        }
        try {
            deferredList.awaitAll()
            true
        } catch (ex: Exception) {
            context.logger.error(ex) { "Failed to stop device batch. Rolling back." }
            deferredList.mapNotNull { it.getCompletedOrNull() }.forEach { dn ->
                childrenJobs[dn]?.let { job ->
                    try {
                        job.device.start()
                        deviceChanges.emit(DeviceStateEvent.DeviceStarted(dn))
                    } catch (rollbackEx: Exception) {
                        context.logger.error(rollbackEx) { "Failed to rollback start for device $dn" }
                    }
                }
            }
            false
        }
    }

    /**
     * Optionally sets up a distributed transport or message broker for the managed devices.
     * The default implementation logs an informational message.
     */
    public open fun installDistributedTransport() {
        context.logger.info { "installDistributedTransport: Implement or override for custom broker." }
    }

    /**
     * Iterates over all children and calls [checkHealth] on each.
     * This method may be scheduled or called periodically.
     */
    public suspend fun runHealthChecks() {
        childLock.withLock {
            for ((_, child) in childrenJobs) {
                checkHealth(child)
            }
        }
    }

    /**
     * Shuts down the device hub manager by cancelling the parent job.
     */
    public suspend fun shutdown() {
        parentJob.cancelAndJoin()
    }
}

/**
 * A default implementation of [AbstractDeviceHubManager] with typical flows for messages, system logs, and device changes.
 *
 * @param context The parent context.
 * @param dispatcher The [CoroutineDispatcher] for concurrency.
 */
internal class DeviceHubManagerImpl(context: Context, dispatcher: CoroutineDispatcher) : AbstractDeviceHubManager(context, dispatcher) {
    override val messageBus: MutableSharedFlow<DeviceMessage> = MutableSharedFlow(
        replay = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val systemBus: MutableSharedFlow<SystemLogMessage> = MutableSharedFlow(
        replay = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val deviceChanges: MutableSharedFlow<DeviceStateEvent> = MutableSharedFlow(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val eventBus: DefaultEventBus = DefaultEventBus(
        replay = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val transactionManager: TransactionManager = DefaultTransactionManager(eventBus, context.logger)
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
 * A device that supports a composite structure of child components using a [spec].
 *
 * @param D Self type for the device.
 * @param spec The [CompositeControlComponentSpec] describing properties, actions, and children.
 * @param context The parent [Context].
 * @param meta The device's metadata.
 * @param config The [DeviceLifecycleConfig] for this device.
 * @param registry Optionally, a [ComponentRegistry] (defaults to [context.componentRegistry]).
 * @param hubManager A custom [AbstractDeviceHubManager], or a default instance if not provided.
 */
public open class ConfigurableCompositeControlComponent<D : ConfigurableCompositeControlComponent<D>>(
    public open val spec: CompositeControlComponentSpec<D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
    config: DeviceLifecycleConfig = DeviceLifecycleConfig(),
    registry: ComponentRegistry? = null,
    public val hubManager: AbstractDeviceHubManager = DeviceHubManagerImpl(context, config.dispatcher ?: Dispatchers.Default)
) : DeviceBase<D>(context, meta), CompositeControlComponent {

    public val effectiveRegistry: ComponentRegistry? = registry ?: context.componentRegistry

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = spec.properties

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = spec.actions

    /**
     * A shared flow for messages. By default, this delegates to [hubManager.messageBus].
     * Override if a separate bus is required.
     */
    final override val messageBus: MutableSharedFlow<DeviceMessage>
        get() = hubManager.messageBus

    override fun toString(): String = "Device(id=$id, spec=$spec)"

    override val devices: Map<Name, Device>
        get() = hubManager.devices

    private val childConfigs: List<ChildComponentConfig<*>> = spec.childSpecs.values.toList()

    init {
        hubManager.launchGlobal {
            spec.actions.values.forEach { actionSpec ->
                messageFlow
                    .filterIsInstance<ActionExecuteMessage>()
                    .filter { it.action == actionSpec.name }
                    .onEach { msg ->
                        try {
                            val result = execute(actionSpec.name, msg.argument)
                            messageBus.emit(
                                ActionResultMessage(
                                    action = actionSpec.name,
                                    result = result,
                                    requestId = msg.requestId,
                                    sourceDevice = id.asName()
                                )
                            )
                        } catch (ex: Exception) {
                            logger.error(ex) { "Error executing action ${actionSpec.name} on device $id" }
                            messageBus.emit(DeviceMessage.error(ex, id.asName()))
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    /**
     * Instantiates (and synchronously adds) all child devices declared in [spec.childSpecs].
     * For child devices with [LifecycleMode.LINKED], the device is started automatically.
     * For [LifecycleMode.INDEPENDENT], the device is added but not started automatically.
     * For [LifecycleMode.LAZY], the device is added and started only upon explicit request.
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
            hubManager.attachDevice(childCfg.name, childDevice, childCfg.config, childCfg.meta, StartMode.SYNC)
        }
    }

    /**
     * Called when this device is starting.
     * The default logic calls [spec.onOpen], validates the device,
     * and then automatically starts child devices if they are [LifecycleMode.LINKED].
     */
    override suspend fun onStart() {
        with(spec) {
            self.onOpen()
            validate(self)
        }
        hubManager.devices.values.filter { it.lifecycleState == LifecycleState.INITIAL }.forEach { child ->
            val mode = hubManager.childrenJobs[child.id.parseAsName()]?.lifecycleMode
            if (mode != LifecycleMode.LAZY) {
                child.start()
            }
        }
    }

    /**
     * Called when the device stops.
     */
    override suspend fun onStop() {
        hubManager.devices.values.forEach { child ->
            if (child.lifecycleState == LifecycleState.STARTED) {
                launch(child.coroutineContext) {
                    val stopTimeout = hubManager.childrenJobs[child.id.parseAsName()]?.config?.stopTimeout ?: Duration.INFINITE
                    val stopped = withTimeoutOrNull(stopTimeout) { child.stop() }
                    if (stopped == null) {
                        hubManager.childrenJobs[child.id.parseAsName()]?.let { job ->
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
     * Default implementation is no-op; override if needed.
     */
    internal open fun onChildStop() {
        // No operation by default.
    }

    /**
     * Retrieves a child device by its [name].
     *
     * @throws IllegalStateException if the child device is not found or if there is a type mismatch.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <CD : ConfigurableCompositeControlComponent<CD>> getChildDevice(name: Name): CD {
        return hubManager.devices[name] as? CD
            ?: error("Child device $name not found or type mismatch.")
    }

    /**
     * Returns the child's message bus for direct access, or `null` if not found.
     *
     * @param name The unique name of the child device.
     * @return A [SharedFlow] of [DeviceMessage] or `null`.
     */
    public fun getChildMessageBus(name: Name): SharedFlow<DeviceMessage>? = hubManager.getChildMessageBus(name)

    /**
     * Provides a property delegate to retrieve a child device by [name] or by the property name if [name] is null.
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
     * Operator to retrieve a child device by [Name].
     */
    public inline operator fun <reified Dev : Device> get(name: Name): Dev? = devices[name] as? Dev

    /**
     * Operator to retrieve a child device by a string name.
     */
    public inline operator fun <reified Dev : Device> get(name: String): Dev? = this[name.asName()]
}

/**
 * Stops the device with a given [timeout].
 * If the device does not stop within the timeout, a warning is logged.
 *
 * @receiver A [WithLifeCycle] device.
 * @param timeout The maximum time to wait for the device to stop.
 */
public suspend fun WithLifeCycle.stopWithTimeout(timeout: Duration = Duration.INFINITE) {
    val result = withTimeoutOrNull(timeout) { stop() }
    if (result == null) {
        (this as? DeviceBase<*>)?.logger?.warn { "Timeout on stop for device ${this.id}" }
    }
}

/**
 * A simple base class for specifying a [ConfigurableCompositeControlComponent].
 *
 * @param D The type of the device.
 * @param deviceFactory A factory function that creates a device given a [Context] and [Meta].
 */
public abstract class DeviceSpecification<D : ConfigurableCompositeControlComponent<D>>(
    public val deviceFactory: (Context, Meta) -> D
) : CompositeControlComponentSpec<D>()
