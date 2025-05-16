package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.controls.constructor.ConstructorElement
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.StateContainer
import space.kscience.controls.constructor.registerState
import space.kscience.controls.spec.api.CompositeControlComponentSpec
import space.kscience.controls.spec.api.ChildComponentConfig
import space.kscience.controls.spec.api.DeviceSpecification
import space.kscience.controls.spec.config.DeviceLifecycleConfig
import space.kscience.controls.spec.model.*
import space.kscience.controls.spec.runtime.DeviceHubManager
import space.kscience.controls.spec.utils.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.coroutines.CoroutineContext
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.time.Duration


/**
 * Interface for a composite control component that hosts child devices.
 */
public interface CompositeControlComponent : Device {
    public val devices: Map<Name, Device>
}

/**
 * A configurable composite device managing child components via a [spec].
 * Interacts with [DeviceHubManager] for lifecycle and messaging.
 *
 * @param D Self-referential type for the device.
 * @property spec The [CompositeControlComponentSpec] defining this device.
 * @param context The parent [Context].
 * @param meta Device metadata.
 * @property deviceHubManager The [DeviceHubManager] instance.
 * @param timeSource The [TimeSource] for time-dependent operations.
 * @param actionExecutionTimeout Default timeout for executing actions.
 */
public open class ConfigurableCompositeControlComponent<D : ConfigurableCompositeControlComponent<D>>(
    public val spec: CompositeControlComponentSpec<D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
    public val deviceHubManager: DeviceHubManager = context.deviceHubManagerOrNull
        ?: throw IllegalStateException("DeviceHubManager not found for composite device ${meta["name"].string ?: "unnamed"}. Ensure registered or provided."),
    private val timeSource: TimeSource = context.timeSourceOrDefault,
    private val actionExecutionTimeout: Duration = meta["actionTimeout"]?.string?.let {
        ParsingUtils.parseDurationOrNull(it)
    } ?: context.deviceManagerConfig.defaultActionExecutionTimeout
) : DeviceBase<D>(context, meta), CompositeControlComponent {

    final override val properties: Map<String, DevicePropertySpec<D, *>> get() = spec.properties
    final override val actions: Map<String, DeviceActionSpec<D, *, *>> get() = spec.actions
    final override val devices: Map<Name, Device> get() = deviceHubManager.devices

    override fun toString(): String = "CompositeDevice(id=$id, spec=${spec::class.simpleName})"

    protected val stateContainer: StateContainerImpl = StateContainerImpl(this)
    protected val childConfigsFromSpec: List<ChildComponentConfig<*>> = spec.childSpecs.values.toList()
    private val childInitializationStatus = mutableMapOf<Name, Boolean>()
    private val initLock = kotlinx.coroutines.sync.Mutex()

    protected class StateContainerImpl(device: Device) : StateContainer {
        override val context: Context = device.context
        override val coroutineContext: CoroutineContext = device.coroutineContext
        private val elements = mutableSetOf<ConstructorElement>()
        override val constructorElements: Set<ConstructorElement> get() = elements.toSet()
        override fun registerElement(constructorElement: ConstructorElement) { elements.add(constructorElement) }
        override fun unregisterElement(constructorElement: ConstructorElement) { elements.remove(constructorElement) }
    }

    init {
        spec.states.values.forEach { stateContainer.registerState(it) }

        launch(CoroutineName("ActionHandler-init-$id")) {
            spec.actions.forEach { (actionName, _) ->
                launch(CoroutineName("ActionHandler-$actionName-$id")) {
                    messageFlow.filterIsInstance<ActionExecuteMessage>()
                        .filter { it.action == actionName && it.targetDevice == id.asName() }
                        .collect { msg ->
                            launch(CoroutineName("ActionExec-$actionName-${msg.requestId}-$id")) {
                                val response: ActionResultMessage = try {
                                    val result = withTimeoutOrNull(actionExecutionTimeout) {
                                        execute(actionName, msg.argument)
                                    }
                                    if (result == null) throw DeviceTimeoutException("Action '$actionName' on '$id' timed out after $actionExecutionTimeout.")
                                    ActionResultMessage(actionName, result, requestId = msg.requestId, sourceDevice = id.asName(), time = timeSource.now())
                                } catch (ex: TimeoutCancellationException) {
                                    logger.error(ex) { "Action '$actionName' on '$id' timed out." }
                                    val timeoutEx = DeviceTimeoutException("Action '$actionName' on '$id' timed out.", ex)
                                    ActionResultMessage(actionName, null, timeoutEx.toSerializableFailure(), msg.requestId, id.asName(), time = timeSource.now())
                                } catch (ex: DeviceException) {
                                    logger.error(ex) { "Error executing action '$actionName' on '$id'." }
                                    ActionResultMessage(actionName, null, ex.toSerializableFailure(), msg.requestId, id.asName(), time = timeSource.now())
                                } catch (ex: Exception) {
                                    logger.error(ex) { "Unexpected error executing action '$actionName' on '$id'." }
                                    val opEx = DeviceOperationException("Action '$actionName' on '$id' failed unexpectedly.", ex)
                                    ActionResultMessage(actionName, null, opEx.toSerializableFailure(), msg.requestId, id.asName(), time = timeSource.now())
                                }
                                deviceHubManager.messagingSystem.publish(response)
                            }
                        }
                }
            }
        }
    }

    public suspend fun initChildren() {
        for (childSpecConfig in childConfigsFromSpec) {
            if (initLock.withLock { childInitializationStatus[childSpecConfig.name] == true }) {
                logger.debug { "Child '${childSpecConfig.name}' for '$id' already initialized."}
                continue
            }
            val effectiveChildConfig = childSpecConfig.config
            try {
                @Suppress("UNCHECKED_CAST")
                val childDevice: Device = if (childSpecConfig.spec is DeviceSpecification<*>) {
                    (childSpecConfig.spec as DeviceSpecification<out ConfigurableCompositeControlComponent<*>>)
                        .deviceFactory(context, childSpecConfig.meta ?: Meta.EMPTY)
                } else {
                    ConfigurableCompositeControlComponent(
                        childSpecConfig.spec,
                        context, childSpecConfig.meta ?: Meta.EMPTY, deviceHubManager, timeSource
                    )
                }
                deviceHubManager.attachDevice(
                    childSpecConfig.name, childDevice, effectiveChildConfig,
                    childSpecConfig.meta, StartMode.NONE
                )
                initLock.withLock { childInitializationStatus[childSpecConfig.name] = true }
                logger.debug { "Child '${childSpecConfig.name}' initialized and attached for '$id'." }
            } catch (e: Exception) {
                logger.error(e) { "Error initializing child '${childSpecConfig.name}' for '$id'." }
                initLock.withLock { childInitializationStatus[childSpecConfig.name] = false }
                if (effectiveChildConfig.onError == ChildDeviceErrorHandler.PROPAGATE) {
                    throw DeviceStartupException("Failed to init child '${childSpecConfig.name}' for '$id'.", e)
                }
            }
        }
    }

    override suspend fun onStart() {
        with(spec) { self.onOpen(); validate(self) }
        initChildren()
        coroutineScope {
            childConfigsFromSpec
                .filter {
                    initLock.withLock { childInitializationStatus[it.name] == true } &&
                            it.config.lifecycleMode == LifecycleMode.LINKED
                }
                .map { cfg ->
                    async(CoroutineName("StartChild-${cfg.name}-for-$id")) {
                        try {
                            logger.debug { "Starting LINKED child '${cfg.name}' for '$id'." }
                            deviceHubManager.lifecycleManager.startDevice(cfg.name)
                        } catch (e: Exception) {
                            logger.error(e) { "Error starting LINKED child '${cfg.name}' during parent '$id' start." }
                            if (cfg.config.onError == ChildDeviceErrorHandler.PROPAGATE) {
                                throw DeviceStartupException("Failed to start LINKED child '${cfg.name}' for '$id'.", e)
                            }
                        }
                    }
                }.awaitAll()
        }
        currentCoroutineContext().ensureActive()
    }

    override suspend fun onStop() {
        supervisorScope {
            childConfigsFromSpec.reversed().forEach { cfg ->
                val childName = cfg.name
                if (initLock.withLock { childInitializationStatus[childName] == true } &&
                    cfg.config.lifecycleMode == LifecycleMode.LINKED) {
                    val childDev = deviceHubManager.devices[childName] as? WithLifeCycle
                    if (childDev?.lifecycleState == LifecycleState.STARTED) {
                        launch(CoroutineName("StopChild-$childName-for-$id")) {
                            try {
                                logger.debug { "Stopping LINKED child '$childName' for '$id'." }
                                deviceHubManager.lifecycleManager.stopDevice(childName)
                            } catch (e: Exception) {
                                logger.error(e) { "Error stopping LINKED child '$childName' during parent '$id' stop." }
                            }
                        }
                    }
                }
            }
        }
        with(spec) { self.onClose() }
    }

    @Suppress("UNCHECKED_CAST")
    public fun <CD : Device> getChildDevice(name: Name): CD? = deviceHubManager.devices[name] as? CD

    public fun <CD : Device> childDevice(name: Name? = null): PropertyDelegateProvider<ConfigurableCompositeControlComponent<D>, ReadOnlyProperty<ConfigurableCompositeControlComponent<D>, CD>> =
        PropertyDelegateProvider { _, property ->
            val devName = name ?: property.name.asName()
            val deviceInstance: CD by lazy {
                getChildDevice(devName) ?: throw DeviceNotFoundException("Child device '$devName' (property '${property.name}') not found in '$id'.")
            }
            ReadOnlyProperty { _, _ -> deviceInstance }
        }

    public inline operator fun <reified Dev : Device> get(name: Name): Dev? = getChildDevice(name)
    public inline operator fun <reified Dev : Device> get(name: String): Dev? = this[name.asName()]

    @Suppress("UNCHECKED_CAST")
    public fun <T> getState(name: String): DeviceState<T>? = spec.states[name] as? DeviceState<T>
    @Suppress("UNCHECKED_CAST")
    public fun <T> getMutableState(name: String): MutableDeviceState<T>? = spec.states[name] as? MutableDeviceState<T>

    public fun <T> state(name: String? = null): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, DeviceState<T>>> =
        PropertyDelegateProvider { _, property ->
            val stateName = name ?: property.name
            val stateInst: DeviceState<T> by lazy { getState(stateName) ?: throw IllegalStateException("State '$stateName' not found in '$id'.") }
            ReadOnlyProperty { _, _ -> stateInst }
        }

    public fun <T> mutableState(name: String? = null): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, MutableDeviceState<T>>> =
        PropertyDelegateProvider { _, property ->
            val stateName = name ?: property.name
            val stateInst: MutableDeviceState<T> by lazy { getMutableState(stateName) ?: throw IllegalStateException("MutableState '$stateName' not found or not mutable in '$id'.") }
            ReadOnlyProperty { _, _ -> stateInst }
        }
}

public suspend fun WithLifeCycle.stopWithTimeout(
    timeout: Duration = DeviceLifecycleConfig.Factory.Defaults.DEVICE_STOP_TIMEOUT,
    loggerOverride: Logger? = null
) {
    val logger = loggerOverride ?: (this as? ContextAware)?.context?.logger
    val deviceIdString = (this as? Device)?.id ?: this.toString()
    try {
        withTimeout(timeout) { stop() }
        logger?.info { "Device '$deviceIdString' stopped within timeout $timeout." }
    } catch (_: TimeoutCancellationException) {
        logger?.warn { "Timeout ($timeout) stopping device '$deviceIdString'." }
    } catch (e: Exception) {
        logger?.error(e) { "Error stopping device '$deviceIdString'." }
    }
}
