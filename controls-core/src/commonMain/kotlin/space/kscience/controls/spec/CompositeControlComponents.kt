package space.kscience.controls.spec

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.names.plus
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.ContextBuilder
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag

/**
 * Defines how child device lifecycle should be managed.
 */
public enum class LifecycleMode {
    /**
     * Device starts and stops with parent
     */
    LINKED,

    /**
     * Device is started and stopped independently
     */
    INDEPENDENT,

    /**
     * Device is created but starts only when explicitly requested
     */
    LAZY
}

public sealed class DeviceChangeEvent {
    public abstract val deviceName: Name

    public data class Added(override val deviceName: Name, val device: Device) : DeviceChangeEvent()
    public data class Removed(override val deviceName: Name) : DeviceChangeEvent()
}

public interface ComponentRegistry : ContextAware {
    public fun <D: ConfigurableCompositeControlComponent<*>> getSpec(name: Name): CompositeControlComponentSpec<D>?
}

public class ComponentRegistryManager : AbstractPlugin(), ComponentRegistry {
    private val specs = mutableMapOf<Name, CompositeControlComponentSpec<*>>()

    override val tag: PluginTag = Companion.tag

    override fun <D : ConfigurableCompositeControlComponent<*>> getSpec(name: Name): CompositeControlComponentSpec<D>? {
        try {
            return specs[name] as? CompositeControlComponentSpec<D>
        } catch (e: ClassCastException) {
            logger.error(e) { "Failed to get spec $name" }
        }
        return null
    }

    public fun registerSpec(spec: CompositeControlComponentSpec<*>) {
        specs[(spec as DevicePropertySpec<*, *>).name.asName()] = spec
    }

    public companion object : PluginFactory<ComponentRegistryManager> {
        override val tag: PluginTag = PluginTag("controls.spechub", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): ComponentRegistryManager = ComponentRegistryManager()

    }
}
public val Context.componentRegistry: ComponentRegistry? get() = plugins[ComponentRegistryManager]

public fun ContextBuilder.withSpecHub() {
    plugin(ComponentRegistryManager)
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

    public fun build(): DeviceLifecycleConfig = DeviceLifecycleConfig(
        lifecycleMode, messageBuffer, startDelay, startTimeout, stopTimeout, coroutineScope, dispatcher, onError
    )
}

public fun DeviceLifecycleConfigBuilder.linked() {
    lifecycleMode = LifecycleMode.LINKED
}

public fun DeviceLifecycleConfigBuilder.independent() {
    lifecycleMode = LifecycleMode.INDEPENDENT
}

public fun DeviceLifecycleConfigBuilder.lazy() {
    lifecycleMode = LifecycleMode.LAZY
}

public fun DeviceLifecycleConfigBuilder.restartOnError() {
    onError = ChildDeviceErrorHandler.RESTART
}

public fun DeviceLifecycleConfigBuilder.propagateError() {
    onError = ChildDeviceErrorHandler.PROPAGATE
}

public fun DeviceLifecycleConfigBuilder.withCustomTimeout(timeout: Duration) {
    startTimeout = timeout
    stopTimeout = timeout
}

@OptIn(InternalDeviceAPI::class)
public abstract class CompositeControlComponentSpec<D : Device>() : CompositeDeviceSpec<D> {

    private val _properties = hashMapOf<String, DevicePropertySpec<D, *>>(
        DeviceMetaPropertySpec.name to DeviceMetaPropertySpec
    )

    override val properties: Map<String, DevicePropertySpec<D, *>> get() = _properties

    private val _actions = hashMapOf<String, DeviceActionSpec<D, *, *>>()

    override val actions: Map<String, DeviceActionSpec<D, *, *>> get() = _actions

    private val _childSpecs = mutableMapOf<String, ChildComponentConfig<*>>()

    override val childSpecs: Map<String, ChildComponentConfig<*>> get() = _childSpecs

    public fun <CDS: CompositeControlComponentSpec<CD>, CD: ConfigurableCompositeControlComponent<CD>> childSpec(
        deviceName: String? = null,
        specName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {},
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            ReadOnlyProperty { _, _ ->
                val childSpecName = specName ?: property.name.asName()
                val nameForDevice = deviceName?.asName() ?: property.name.asName()
                val config = DeviceLifecycleConfigBuilder().apply(configBuilder).build()
                val meta = metaBuilder?.let { Meta(it) }
                val spec = (thisRef as ConfigurableCompositeControlComponent<*>).context.componentRegistry?.getSpec<CD>(childSpecName) ?: error("Spec with name '$specName' is not found")
                val childComponentConfig = object : ChildComponentConfig<CD>{
                    override val spec: CompositeControlComponentSpec<CD> = spec
                    override val config: DeviceLifecycleConfig = config
                    override val meta: Meta? = meta
                    override val name: Name = nameForDevice
                }
                _childSpecs[property.name] = childComponentConfig
                childComponentConfig.spec
            }
        }

    override fun validate(device: D) {
        properties.map { it.value.descriptor }.forEach { specProperty ->
            check(specProperty in device.propertyDescriptors) { "Property ${specProperty.name} not registered in ${device.id}" }
        }
        actions.map { it.value.descriptor }.forEach { specAction ->
            check(specAction in device.actionDescriptors) { "Action ${specAction.name} not registered in ${device.id}" }
        }
    }

    override fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P {
        _properties[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    override fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
        _actions[deviceAction.name] = deviceAction
        return deviceAction
    }

    override fun createPropertyDescriptorInternal(
        propertyName: String,
        converter: MetaConverter<*>,
        mutable: Boolean,
        property: KProperty<*>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
    ): PropertyDescriptor {
        return propertyDescriptor(propertyName) {
            this.mutable = mutable
            converter.descriptor?.let { converterDescriptor ->
                metaDescriptor {
                    from(converterDescriptor)
                }
            }
            fromSpec(property)
            descriptorBuilder()
        }
    }

    override fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        property: KProperty<*>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit,
    ): ActionDescriptor {
        return actionDescriptor(actionName) {
            inputConverter.descriptor?.let { converterDescriptor ->
                inputMeta {
                    from(converterDescriptor)
                }
            }
            outputConverter.descriptor?.let { converterDescriptor ->
                outputMeta {
                    from(converterDescriptor)
                }
            }
            fromSpec(property)
            descriptorBuilder()
        }
    }

    override fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?,
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: CompositeControlComponentSpec<D>, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptorInternal(
                propertyName = propertyName,
                converter = converter,
                mutable = false,
                property = property,
                descriptorBuilder = descriptorBuilder
            )
            val deviceProperty = registerProperty(object : DevicePropertySpec<D, T> {
                override val descriptor = descriptor
                override val converter = converter

                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }
            })
            ReadOnlyProperty { _, _ ->
                deviceProperty
            }
        }


    override fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: CompositeControlComponentSpec<D>, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptorInternal(
                propertyName = propertyName,
                converter = converter,
                mutable = true,
                property = property,
                descriptorBuilder = descriptorBuilder
            )
            val deviceProperty = registerProperty(object : MutableDevicePropertySpec<D, T> {
                override val descriptor = descriptor
                override val converter = converter
                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }

                override suspend fun write(device: D, value: T): Unit = withContext(device.coroutineContext) {
                    device.write(propertyName, value)
                }
            })
            ReadOnlyProperty { _, _ ->
                deviceProperty
            }
        }


    override fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit,
        name: String?,
        execute: suspend D.(I) -> O,
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _: CompositeControlComponentSpec<D>, property ->
            val actionName = name ?: property.name
            val descriptor = createActionDescriptor(
                actionName = actionName,
                inputConverter = inputConverter,
                outputConverter = outputConverter,
                property = property,
                descriptorBuilder = descriptorBuilder
            )
            val deviceAction = registerAction(object : DeviceActionSpec<D, I, O> {
                override val descriptor = descriptor
                override val inputConverter = inputConverter
                override val outputConverter = outputConverter
                override suspend fun execute(device: D, input: I): O = withContext(device.coroutineContext) {
                    device.execute(input)
                }
            })

            ReadOnlyProperty { _, _ ->
                deviceAction
            }
        }

    override suspend fun D.onOpen() {}
    override suspend fun D.onClose() {}

}

public fun <D : Device> CompositeControlComponentSpec<D>.unitAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.() -> Unit,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    action(
        MetaConverter.unit,
        MetaConverter.unit,
        descriptorBuilder,
        name
    ) {
        execute()
    }


public fun <D : Device> CompositeControlComponentSpec<D>.metaAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(Meta) -> Meta,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    action(
        MetaConverter.meta,
        MetaConverter.meta,
        descriptorBuilder,
        name
    ) {
        execute(it)
    }

/**
 *  Basic interface for device description
 */
public interface CompositeDeviceSpec<D : Device> {
    public val properties: Map<String, DevicePropertySpec<D, *>>

    public val actions: Map<String, DeviceActionSpec<D, *, *>>

    public val childSpecs: Map<String, ChildComponentConfig<*>>

    /**
     *  Called on `start()`
     */
    public suspend fun D.onOpen()

    /**
     * Called on `stop()`
     */
    public suspend fun D.onClose()

    /**
     * Registers a property in the spec.
     */
    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P

    /**
     * Registers an action in the spec.
     */
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    public fun validate(device: D)

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
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, T>>>

    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit,
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, T>>>

    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(I) -> O,
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, I, O>>>
}

public data class DeviceLifecycleConfig(
    val lifecycleMode: LifecycleMode = LifecycleMode.LINKED,
    val messageBuffer: Int = 1000,
    val startDelay: Duration = Duration.ZERO,
    val startTimeout: Duration? = null,
    val stopTimeout: Duration? = null,
    val coroutineScope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher? = null,
    val onError: ChildDeviceErrorHandler = ChildDeviceErrorHandler.RESTART
) {
    init {
        require(messageBuffer > 0) { "Message buffer size must be positive." }
        startTimeout?.let { require(it.isPositive()) { "Start timeout must be positive." } }
        stopTimeout?.let { require(it.isPositive()) { "Stop timeout must be positive." } }
    }
}

public enum class ChildDeviceErrorHandler {
    IGNORE,
    RESTART,
    STOP_PARENT,
    PROPAGATE
}

/**
 * Base class for managing child devices. Manages lifecycle and message flow.
 */
public abstract class AbstractDeviceHubManager(
    public val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    internal val childrenJobs: MutableMap<Name, ChildJob> = mutableMapOf()
    public val devices: Map<Name, Device> get() = childrenJobs.mapValues { it.value.device }

    internal data class ChildJob(
        val device: Device,
        val job: Job,
        val lifecycleMode: LifecycleMode,
        val messageBus: MutableSharedFlow<DeviceMessage>,
        val meta: Meta? = null
    )

    /**
     * A centralized bus for messages
     */
    public abstract val messageBus: MutableSharedFlow<DeviceMessage>

    /**
     * A centralized bus for device change events
     */
    public abstract val deviceChanges: MutableSharedFlow<DeviceChangeEvent>


    /**
     * Launches a child device with a specific lifecycle mode and error handling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun launchChild(name: Name, device: Device, config: DeviceLifecycleConfig, meta: Meta? = null): ChildJob {
        val childMessageBus = MutableSharedFlow<DeviceMessage>(
            replay = config.messageBuffer,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val childScope = config.coroutineScope ?: context
        val job = childScope.launch(dispatcher + CoroutineName("Child device $name")) {
            try {
                if (config.lifecycleMode != LifecycleMode.INDEPENDENT) {
                    if (config.lifecycleMode == LifecycleMode.LINKED || device.lifecycleState == LifecycleState.STARTING){
                        delay(config.startDelay)
                        withTimeoutOrNull(config.startTimeout ?: Duration.INFINITE) {
                            device.start()
                        } ?: error("Timeout on start for $name")
                    }
                }

                device.messageFlow.collect { message ->
                    childMessageBus.emit(message.changeSource { name.plus(it) })
                    messageBus.emit(message.changeSource { name.plus(it) })
                }
            } catch (ex: Exception) {
                val errorMessage = DeviceMessage.error(ex, name)
                messageBus.emit(errorMessage)

                when (config.onError) {
                    ChildDeviceErrorHandler.IGNORE -> context.logger.error(ex) { "Error in child device $name ignored" }
                    ChildDeviceErrorHandler.RESTART -> {
                        context.logger.error(ex) { "Error in child device $name, restarting" }
                        removeDevice(name)
                        childrenJobs[name] = launchChild(name, device, config, meta)
                    }
                    ChildDeviceErrorHandler.STOP_PARENT -> {
                        context.logger.error(ex) { "Error in child device $name, stopping parent" }
                        coroutineContext[Job]?.cancelAndJoin()
                    }
                    ChildDeviceErrorHandler.PROPAGATE -> {
                        context.logger.error(ex) { "Error in child device $name propagated to parent" }
                        throw ex
                    }
                }
            } finally {
                childrenJobs.remove(name)
                clearReplayCache(childMessageBus)
                deviceChanges.emit(DeviceChangeEvent.Removed(name))
                messageBus.emit(DeviceLogMessage("Device $name stopped", sourceDevice = name))
                if (device is ConfigurableCompositeControlComponent<*>) {
                    device.onChildStop()
                }
            }
        }
        return ChildJob(device, job, config.lifecycleMode, childMessageBus, meta)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> clearReplayCache(mutableSharedFlow: MutableSharedFlow<T>){
        val cached = mutableSharedFlow.replayCache
        mutableSharedFlow.resetReplayCache()
        cached.forEach { mutableSharedFlow.tryEmit(it) }
    }

    /**
     * Add a device to the hub and manage its lifecycle according to its spec
     */
    public fun addDevice(name: Name, device: Device, config: DeviceLifecycleConfig, meta: Meta? = null) {
        val existingDevice = devices[name]
        if (existingDevice != null) {
            if(existingDevice == device) {
                error("Device with name $name is already installed")
            }
            context.launch(device.coroutineContext) { existingDevice.stopWithTimeout(config.stopTimeout ?: Duration.INFINITE) }
        }
        childrenJobs[name] = launchChild(name, device, config, meta)

        context.launch {
            deviceChanges.emit(DeviceChangeEvent.Added(name, device))
            messageBus.emit(DeviceLogMessage("Device $name added", sourceDevice = name))
        }
    }

    public fun removeDevice(name: Name) {
        childrenJobs[name]?.let { childJob ->
            context.launch(childJob.device.coroutineContext) {
                val timeout = when (childJob.lifecycleMode) {
                    LifecycleMode.INDEPENDENT -> childJob.device.meta["stopTimeout".asName()]?.value?.let {
                        Duration.parse(it.toString())
                    } ?: Duration.INFINITE

                    else -> Duration.INFINITE
                }
                withTimeoutOrNull(timeout) {
                    childJob.job.cancelAndJoin()
                    childJob.device.stop()
                } ?: error("Timeout on stop for $name")
            }
            childrenJobs.remove(name)
            context.launch {
                messageBus.emit(DeviceLogMessage("Device $name removed", sourceDevice = name))
                deviceChanges.emit(DeviceChangeEvent.Removed(name))
            }
        }
    }

    /**
     * Change lifecycle mode of a child device
     */
    public fun changeLifecycleMode(name: Name, mode: LifecycleMode) {
        val job = childrenJobs[name] ?: error("Device with name '$name' is not found")
        val config = DeviceLifecycleConfig(lifecycleMode = mode, messageBuffer = job.messageBus.replayCache.size)
        context.launch {
            job.job.cancelAndJoin()
            childrenJobs[name] = launchChild(name, job.device, config, job.meta)
        }
    }

    /**
     * Get local message bus for a child device
     */
    public fun getChildMessageBus(name: Name) : MutableSharedFlow<DeviceMessage>? = childrenJobs[name]?.messageBus

    /**
     * Method for explicit call when child device is stopped.
     */
    internal open fun onChildStop(){

    }
}


private class DeviceHubManagerImpl(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.Default) : AbstractDeviceHubManager(context, dispatcher){
    override val messageBus: MutableSharedFlow<DeviceMessage> = MutableSharedFlow(
        replay = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val deviceChanges: MutableSharedFlow<DeviceChangeEvent> = MutableSharedFlow(replay = 1)
}

/**
 * An interface for a device that contains other devices as children.
 */
public interface CompositeControlComponent : Device {
    /**
     * A centralized flow of all device messages from this node and all its children
     */
    public val messageBus: SharedFlow<DeviceMessage>

    /**
     * A map of child devices
     */
    public val devices: Map<Name, Device>
}

/**
 * A base class for devices created from specification, using AbstractDeviceHubManager for children management
 */
public open class ConfigurableCompositeControlComponent<D : Device>(
    public open val spec: CompositeControlComponentSpec<D>,
    context: Context,
    meta: Meta = Meta.EMPTY,
    config: DeviceLifecycleConfig = DeviceLifecycleConfig(),
    private val hubManager: AbstractDeviceHubManager = DeviceHubManagerImpl(context, config.dispatcher ?: Dispatchers.Default),
) : DeviceBase<D>(context, meta), CompositeControlComponent {

    override val properties: Map<String, DevicePropertySpec<D, *>>
        get() = spec.properties

    override val actions: Map<String, DeviceActionSpec<D, *, *>>
        get() = spec.actions

    final override val messageBus: MutableSharedFlow<DeviceMessage>
        get() = hubManager.messageBus

    public val deviceChanges: MutableSharedFlow<DeviceChangeEvent>
        get() = hubManager.deviceChanges

    public val aggregatedMessageFlow: SharedFlow<DeviceMessage>
        get() = hubManager.messageBus

    init {
        spec.childSpecs.forEach{ (name, childSpec) ->
            val childDevice = ConfigurableCompositeControlComponent(childSpec.spec, context, childSpec.meta ?: Meta.EMPTY, childSpec.config)
            addDevice(childSpec.name, childDevice, childSpec.config, childSpec.meta)
        }

        spec.actions.values.forEach { actionSpec ->
            launch {
                val actionName = actionSpec.name
                messageFlow.filterIsInstance<ActionExecuteMessage>().filter { it.action == actionName }.onEach {
                    val result = execute(actionName, it.argument)
                    messageBus.emit(
                        ActionResultMessage(
                            action = actionName,
                            result = result,
                            requestId = it.requestId,
                            sourceDevice = id.asName()
                        )
                    )
                }.launchIn(this)
            }
        }
    }

    override suspend fun onStart() {
        with(spec) {
            self.onOpen()
            validate(self)
        }
        hubManager.devices.values.filter {
            it.lifecycleState != LifecycleState.STARTED && it.lifecycleState != LifecycleState.STARTING
        }.forEach {
            if (hubManager.childrenJobs[it.id.parseAsName()]?.lifecycleMode != LifecycleMode.LAZY) {
                it.start()
            }
        }
    }

    private suspend fun getTimeout(device: Device): Duration {
        return (hubManager.childrenJobs[device.id.parseAsName()]?.lifecycleMode ?:  LifecycleMode.LINKED).let{
            if(it == LifecycleMode.INDEPENDENT)
                it.name.let{ meta["stopTimeout".parseAsName()]?.let { durationMeta ->
                    Duration.parse(durationMeta.value.toString())
                } ?: Duration.INFINITE }
            else Duration.INFINITE
        }
    }

    override suspend fun onStop() {
        hubManager.devices.values.forEach {
            launch(it.coroutineContext){
                withTimeoutOrNull(getTimeout(it)){
                    it.stop()
                }
            }
        }
        with(spec) {
            self.onClose()
        }
    }

    override fun toString(): String = "Device(spec=$spec)"

    internal open fun onChildStop() {
    }

    /**
     * Add existing device to this hub
     */
    private fun addDevice(name: Name = device.id.asName(), device: Device, config: DeviceLifecycleConfig = DeviceLifecycleConfig(), meta: Meta? = null) {
        hubManager.addDevice(name, device, config, meta)
    }

    /**
     * Remove a child device from the hub by name.
     */
    private fun removeDevice(name: Name) {
        hubManager.removeDevice(name)
    }

    /**
     * Get list of all children devices
     */
    public override val devices: Map<Name, Device>
        get() = hubManager.devices

    /**
     * Get child device from this hub by name
     */
    public fun <CD : ConfigurableCompositeControlComponent<CD>> getChildDevice(name: Name): ConfigurableCompositeControlComponent<CD> =
        hubManager.devices[name] as? ConfigurableCompositeControlComponent<CD>? ?: error("Device $name not found")

    public fun <CD: ConfigurableCompositeControlComponent<CD>> childDevice(name: Name? = null):
            PropertyDelegateProvider<ConfigurableCompositeControlComponent<D>, ReadOnlyProperty<ConfigurableCompositeControlComponent<D>, CD>>
            = PropertyDelegateProvider{ thisRef, property ->
        ReadOnlyProperty{ _, _ ->
            val deviceName = name ?: property.name.asName()
            thisRef.devices[deviceName] as? CD ?: error("Device $deviceName not found")
        }
    }

    /**
     * Get child device message bus by name
     */
    public fun getChildMessageBus(name: Name): SharedFlow<DeviceMessage>? = hubManager.getChildMessageBus(name)

    /**
     * Get device, using delegate method
     */
    public inline operator fun <reified D : Device> get(name: Name): D? = devices[name] as? D

    /**
     * Get device, using delegate method
     */
    public inline operator fun <reified D : Device> get(name: String): D? = devices[name.asName()] as? D

}

public suspend fun WithLifeCycle.stopWithTimeout(timeout: Duration = Duration.INFINITE) {
    withTimeoutOrNull(timeout) {
        stop()
    }
}

public interface ChildComponentConfig<CD: Device>{
    public val spec: CompositeControlComponentSpec<CD>
    public val config: DeviceLifecycleConfig
    public val meta: Meta?
    public val name: Name
}
