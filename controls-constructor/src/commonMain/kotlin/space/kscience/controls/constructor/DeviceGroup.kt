package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.*
import space.kscience.controls.api.LifecycleState.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Laminate
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.get
import space.kscience.dataforge.names.parseAsName
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock


/**
 * A mutable group of devices and properties to be used for lightweight design and simulations.
 */
public open class DeviceGroup(
    final override val context: Context,
    override val meta: Meta,
) : DeviceHub, CachingDevice {

    private class Property<T>(
        val state: DeviceState<T>,
        val converter: MetaConverter<T>,
        val descriptor: PropertyDescriptor,
    ) {
        val valueAsMeta get() = converter.convert(state.value)

        fun setMeta(meta: Meta) {
            check(state is MutableDeviceState) { "Can't write to read-only property" }

            state.value = converter.read(meta)
        }
    }

    private class Action<T, R>(
        val inputConverter: MetaConverter<T>,
        val outputConverter: MetaConverter<R>,
        val descriptor: ActionDescriptor,
        val action: suspend (T) -> R,
    ) {
        suspend operator fun invoke(argument: Meta?): Meta? = argument?.let { inputConverter.readOrNull(it) }
            ?.let { action(it)?.let { outputConverter.convert(it) } }
    }


    private val sharedMessageFlow = MutableSharedFlow<DeviceMessage>()

    override val messageFlow: Flow<DeviceMessage>
        get() = sharedMessageFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = context.newCoroutineContext(
        SupervisorJob(context.coroutineContext[Job]) +
                CoroutineName("Device $id") +
                CoroutineExceptionHandler { _, throwable ->
                    context.launch {
                        sharedMessageFlow.emit(
                            DeviceErrorMessage(
                                time = clock.now(),
                                errorMessage = throwable.message,
                                errorType = throwable::class.simpleName,
                                errorStackTrace = throwable.stackTraceToString()
                            )
                        )
                    }
                    logger.error(throwable) { "Exception in device $id" }
                }
    )


    private val _devices = hashMapOf<Name, Device>()

    override val devices: Map<Name, Device> = _devices

    /**
     * Register and initialize (synchronize child's lifecycle state with group state) a new device in this group
     */
    @OptIn(DFExperimental::class)
    public open fun <D : Device> install(token: Name, device: D): D {
        require(_devices[token] == null) { "A child device with name $token already exists" }
        //start the child device if needed
        if (lifecycleState == STARTED || lifecycleState == STARTING) launch { device.start() }
        _devices[token] = device
        return device
    }

    private val properties: MutableMap<Name, Property<*>> = hashMapOf()

    /**
     * Register a new property based on [DeviceState]. Properties could be modified dynamically
     */
    public open fun <T, S : DeviceState<T>> registerProperty(
        converter: MetaConverter<T>,
        descriptor: PropertyDescriptor,
        state: S,
    ): S {
        val name = descriptor.name.parseAsName()
        require(properties[name] == null) { "Can't add property with name $name. It already exists." }
        properties[name] = Property(state, converter, descriptor)
        state.valueFlow.map(converter::convert).onEach {
            sharedMessageFlow.emit(
                PropertyChangedMessage(
                    time = clock.now(),
                    property = descriptor.name,
                    value = it
                )
            )
        }.launchIn(this)
        return state
    }

    private val actions: MutableMap<Name, Action<*, *>> = hashMapOf()

    public fun <T, R> registerAction(
        inputConverter: MetaConverter<T>,
        outputConverter: MetaConverter<R>,
        descriptor: ActionDescriptor,
        action: suspend (T) -> R,
    ): suspend (T) -> R {
        val name = descriptor.name.parseAsName()
        require(actions[name] == null) { "Can't add action with name $name. It already exists." }
        actions[name] = Action(
            inputConverter = inputConverter,
            outputConverter = outputConverter,
            descriptor = descriptor,
            action = action
        )
        return {
            action(it)
        }
    }

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }

    override suspend fun readProperty(propertyName: String): Meta =
        properties[propertyName.parseAsName()]?.valueAsMeta
            ?: error("Property with name $propertyName not found")

    override fun getProperty(propertyName: String): Meta? = properties[propertyName.parseAsName()]?.valueAsMeta

    override suspend fun invalidate(propertyName: String) {
        //does nothing for this implementation
    }

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        val property = properties[propertyName.parseAsName()] ?: error("Property with name $propertyName not found")
        property.setMeta(value)
    }


    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val action: Action<*, *> = actions[actionName] ?: error("Action with name $actionName not found")
        return action(argument)
    }

    final override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    private suspend fun setLifecycleState(lifecycleState: LifecycleState) {
        this.lifecycleState = lifecycleState
        sharedMessageFlow.emit(
            DeviceLifeCycleMessage(clock.now(), lifecycleState)
        )
    }


    override suspend fun start() {
        setLifecycleState(STARTING)
        super.start()
        devices.values.forEach {
            it.start()
        }
        setLifecycleState(STARTED)
    }

    override suspend fun stop() {
        devices.values.forEach {
            it.stop()
        }
        setLifecycleState(STOPPED)
        super.stop()
    }

    override val clock: Clock = context.clock

    public companion object {

    }
}

public fun <T> DeviceGroup.registerAsProperty(propertySpec: DevicePropertySpec<*, T>, state: DeviceState<T>) {
    registerProperty(propertySpec.converter, propertySpec.descriptor, state)
}

public fun DeviceManager.registerDeviceGroup(
    name: String = "@group",
    meta: Meta = Meta.EMPTY,
    block: DeviceGroup.() -> Unit,
): DeviceGroup {
    val group = DeviceGroup(context, meta).apply(block)
    install(name, group)
    return group
}

public fun Context.registerDeviceGroup(
    name: String = "@group",
    meta: Meta = Meta.EMPTY,
    block: DeviceGroup.() -> Unit,
): DeviceGroup = request(DeviceManager).registerDeviceGroup(name, meta, block)

///**
// * Register a device at given [path] path
// */
//public fun <D : Device> DeviceGroup.install(path: Path, device: D): D {
//    return when (path.length) {
//        0 -> error("Can't use empty path for a child device")
//        1 -> install(path.first().name, device)
//        else -> getOrCreateGroup(path.cutLast()).install(path.tokens.last(), device)
//    }
//}

public fun <D : Device> DeviceGroup.install(name: String, device: D): D = install(name.parseAsName(), device)

public fun <D : Device> DeviceGroup.install(device: D): D = install(device.id, device)

/**
 * Add a device creating intermediate groups if necessary. If device with given [name] already exists, throws an error.
 * @param name the name of the device in the group
 * @param factory a factory used to create a device
 * @param deviceMeta meta override for this specific device
 * @param metaLocation location of the template meta in parent group meta
 */
public fun <D : Device> DeviceGroup.install(
    name: Name,
    factory: Factory<D>,
    deviceMeta: Meta? = null,
    metaLocation: Name = name,
): D {
    val newDevice = factory.build(context, Laminate(deviceMeta, meta[metaLocation]))
    install(name, newDevice)
    return newDevice
}

public fun <D : Device> DeviceGroup.install(
    name: String,
    factory: Factory<D>,
    metaLocation: Name = name.parseAsName(),
    metaBuilder: (MutableMeta.() -> Unit)? = null,
): D = install(name.parseAsName(), factory, metaBuilder?.let { Meta(it) }, metaLocation)

/**
 * Create or edit a group with a given [name].
 */
public fun DeviceGroup.registerDeviceGroup(name: Name, block: DeviceGroup.() -> Unit): DeviceGroup =
    install(name, DeviceGroup(context, meta).apply(block))

public fun DeviceGroup.registerDeviceGroup(name: String, block: DeviceGroup.() -> Unit): DeviceGroup =
    registerDeviceGroup(name.parseAsName(), block)

/**
 * Register read-only property based on [state]
 */
public fun <T : Any> DeviceGroup.registerAsProperty(
    name: String,
    converter: MetaConverter<T>,
    state: DeviceState<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
) {
    registerProperty(
        converter,
        PropertyDescriptor(name).apply(descriptorBuilder),
        state
    )
}

/**
 * Register a mutable property based on mutable [state]
 */
public fun <T : Any> DeviceGroup.registerMutableProperty(
    name: String,
    converter: MetaConverter<T>,
    state: MutableDeviceState<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
) {
    registerProperty(
        converter,
        PropertyDescriptor(name).apply(descriptorBuilder),
        state
    )
}


/**
 * Create a new virtual mutable state and a property based on it.
 * @return the mutable state used in property
 */
public fun <T : Any> DeviceGroup.registerVirtualProperty(
    name: String,
    initialValue: T,
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    callback: (T) -> Unit = {},
): MutableDeviceState<T> {
    val state = MutableDeviceState<T>(initialValue, callback)
    registerMutableProperty(name, converter, state, descriptorBuilder)
    return state
}
