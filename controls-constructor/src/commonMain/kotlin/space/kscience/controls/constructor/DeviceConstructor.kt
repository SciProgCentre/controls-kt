package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.*
import space.kscience.controls.api.LifecycleState.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.InternalDeviceAPI
import space.kscience.controls.time.clock
import space.kscience.controls.time.deviceDispatcher
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Laminate
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.get
import space.kscience.dataforge.names.parseAsName
import kotlin.coroutines.CoroutineContext
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Clock
import kotlin.time.Duration


/**
 * A mutable group of devices and properties to be used for lightweight design and simulations.
 */
public open class DeviceConstructor(
    final override val context: Context,
    override val meta: Meta = Meta.EMPTY,
) : Device, DeviceTree, CachingDevice, MutableConstructor {

    override val device: Device? get() = this

    private val _constructorElements: MutableSet<ConstructorElement> = mutableSetOf()
    override val constructorElements: Set<ConstructorElement> get() = _constructorElements

    override fun registerElement(constructorElement: ConstructorElement) {
        _constructorElements.add(constructorElement)
    }

    override fun unregisterElement(constructorElement: ConstructorElement) {
        _constructorElements.remove(constructorElement)
    }

    private class Property<T>(
        val state: ValueState<T>,
        val converter: MetaConverter<T>,
        val descriptor: PropertyDescriptor,
    ) {
        val valueAsMeta get() = converter.convert(state.value)

        fun asMetaValueState() = state.map(converter::convert)

        fun setMeta(meta: Meta) {
            check(state is MutableValueState) { "Can't write to read-only property" }

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
                context.deviceDispatcher +
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


    private val _devices = hashMapOf<String, DeviceTree>()

    override val children: Map<String, DeviceTree> get() = _devices

    /**
     * Register and initialize (synchronize child's lifecycle state with group state) a new device tree in this group.
     */
    public fun <DT : DeviceTree> installTree(deviceName: String, child: DT): DT {
        require(_devices[deviceName] == null) { "A child device with name $deviceName already exists" }
        //start the child device if this device is started
        if (isStarted()) child.start()
        _devices[deviceName] = child
        if (child is Constructor) {
            registerElement(ChildConstructorElement(Name.of(deviceName), child))
        }
        return child
    }

    private val properties: MutableMap<Name, Property<*>> = hashMapOf()

    /**
     * Get property with given [propertyName] as a [ValueState]. If the property is not found, throws an error.
     */
    public fun <T> propertyAsState(propertyName: String, converter: MetaConverter<T>): ValueState<T> {
        val prop = properties[propertyName.parseAsName()] ?: error("Property with name $propertyName not found")
        return if (prop.converter == converter) {
            @Suppress("UNCHECKED_CAST")
            prop.state as ValueState<T>
        } else {
            //perform double meta conversion on read if inner converter and outer converter are different
            prop.asMetaValueState().map(converter::read)
        }
    }

    /**
     * Register a new property based on [ValueState]. Properties could be modified dynamically
     */
    public fun <T, S : ValueState<T>> registerProperty(
        converter: MetaConverter<T>,
        descriptor: PropertyDescriptor,
        state: S,
    ): S {
        val name = descriptor.name.parseAsName()
        require(properties[name] == null) { "Can't add property with name $name. It already exists." }
        properties[name] = Property(state, converter, descriptor)
        state.subscribe().map(converter::convert).onEach {
            sharedMessageFlow.emit(
                PropertyChangedMessage(
                    time = clock.now(),
                    property = descriptor.name,
                    value = it
                )
            )
        }.launchIn(this)
        registerElement(PropertyConstructorElement(this, descriptor.name, state))
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

    override fun getCachedProperty(propertyName: String): Meta? = properties[propertyName.parseAsName()]?.valueAsMeta

    @InternalDeviceAPI
    override fun setCachedProperty(propertyName: String, value: Meta?) {
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
        super<CachingDevice>.start()
        setLifecycleState(STARTING)
        children.values.forEach {
            it.device?.start()
        }
        setLifecycleState(STARTED)
    }

    override suspend fun stop() {
        children.values.forEach {
            it.device?.stop()
        }
        setLifecycleState(STOPPED)
        super<CachingDevice>.stop()
    }

    override val clock: Clock = context.clock

    public companion object
}

/**
 * Register [child] as a [ChildConstructorElement] relative to this one
 */
public fun <T : Constructor> MutableConstructor.child(child: T, name: Name? = null): T {
    registerElement(ChildConstructorElement(name, child))
    return child
}

/**
 * Register and initialize (synchronize child's lifecycle state with group state) a new device in this group
 */
public fun <D : Device> DeviceConstructor.install(deviceName: String, device: D): D {
    installTree(deviceName, device as? DeviceTree ?: DeviceTree(device))
    return device
}

public fun DeviceManager.install(
    name: String = "@group",
    meta: Meta = Meta.EMPTY,
    block: DeviceConstructor.() -> Unit,
): DeviceConstructor {
    val group = DeviceConstructor(context, meta).apply(block)
    install(name, group)

    return group
}

public fun Context.install(
    name: String = "@group",
    meta: Meta = Meta.EMPTY,
    block: DeviceConstructor.() -> Unit,
): DeviceConstructor = request(DeviceManager).install(name, meta, block)

public fun <D : Device> DeviceConstructor.install(device: D): D = install(device.id, device)

/**
 * Add a device creating intermediate groups if necessary. If device with given [name] already exists, throws an error.
 * @param name the name of the device in the group
 * @param factory a factory used to create a device
 * @param deviceMeta meta override for this specific device
 * @param metaLocation location of the template meta in parent group meta
 */
public fun <D : Device> DeviceConstructor.install(
    name: String,
    factory: Factory<D>,
    deviceMeta: Meta? = null,
    metaLocation: Name = Name.of(name),
): D {
    val newDevice = factory.build(context, Laminate(deviceMeta, meta[metaLocation]))
    install(name, newDevice)
    return newDevice
}

/**
 * Add a device tree creating intermediate groups if necessary. If device with given [name] already exists, throws an error.
 */
public fun <DT: DeviceTree> DeviceConstructor.installTree(
    name: String,
    factory: Factory<DT>,
    deviceMeta: Meta? = null,
    metaLocation: Name = Name.of(name),
): DT {
    val newDevice = factory.build(context, Laminate(deviceMeta, meta[metaLocation]))
    installTree(name, newDevice)
    return newDevice
}

/**
 * Create or edit a group with a given [name].
 */
public fun DeviceConstructor.install(name: String, block: DeviceConstructor.() -> Unit): DeviceConstructor =
    install(name, DeviceConstructor(context, meta).apply(block))

/**
 * Register read-only property based on [state]
 */
public fun <T : Any> DeviceConstructor.registerProperty(
    name: String,
    converter: MetaConverter<T>,
    state: ValueState<T>,
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
public fun <T : Any> DeviceConstructor.registerMutableProperty(
    name: String,
    converter: MetaConverter<T>,
    state: MutableValueState<T>,
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
public fun <T : Any> DeviceConstructor.registerVirtualProperty(
    name: String,
    initialValue: T,
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): MutableValueState<T> {
    val state = MutableValueState<T>(initialValue)
    registerMutableProperty(name, converter, state, descriptorBuilder)
    return state
}

/**
 * Register a child device using a delegate provider
 */
public fun <D : Device> DeviceConstructor.device(
    factory: Factory<D>,
    meta: Meta? = null,
    nameOverride: String? = null,
    metaLocation: Name? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, D>> =
    PropertyDelegateProvider { _: DeviceConstructor, property: KProperty<*> ->
        val name = nameOverride ?: property.name
        val device = install(name, factory, meta, metaLocation ?: Name.of(name))
        ReadOnlyProperty { _: DeviceConstructor, _ ->
            device
        }
    }

public fun <D : Device> DeviceConstructor.device(
    device: D,
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, D>> =
    PropertyDelegateProvider { _: DeviceConstructor, property: KProperty<*> ->
        val name = nameOverride ?: property.name
        install(name, device)
        ReadOnlyProperty { _: DeviceConstructor, _ ->
            device
        }
    }

/**
 * Register a property and provide a direct reader for it
 */
public fun <T, S : ValueState<T>> DeviceConstructor.property(
    converter: MetaConverter<T>,
    state: S,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, S>> =
    PropertyDelegateProvider { _: DeviceConstructor, property ->
        val name = nameOverride ?: property.name
        val descriptor = PropertyDescriptor(name).apply(descriptorBuilder)
        registerProperty(converter, descriptor, state)
        ReadOnlyProperty { _: DeviceConstructor, _ ->
            state
        }
    }

/**
 * Register an external state as a property
 */
public fun <T : Any> DeviceConstructor.property(
    metaConverter: MetaConverter<T>,
    reader: suspend () -> T,
    readInterval: Duration,
    initialState: T,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, ValueState<T>>> = property(
    converter = metaConverter,
    state = ValueState.external(
        context = context,
        readInterval = readInterval,
        initialValue = initialState,
        reader = reader
    ),
    descriptorBuilder = descriptorBuilder,
    nameOverride = nameOverride,
)

/**
 * Create and register a mutable external state as a property
 */
public fun <T : Any> DeviceConstructor.mutableProperty(
    metaConverter: MetaConverter<T>,
    reader: suspend () -> T,
    writer: suspend (T) -> Unit,
    readInterval: Duration,
    initialState: T,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, MutableValueState<T>>> = property(
    converter = metaConverter,
    state = ValueState.external(
        context = context,
        readInterval = readInterval,
        initialValue = initialState,
        reader = reader,
        writer = writer
    ),
    descriptorBuilder = descriptorBuilder,
    nameOverride = nameOverride,
)

/**
 * Create and register a virtual mutable property
 */
public fun <T> DeviceConstructor.virtualProperty(
    metaConverter: MetaConverter<T>,
    initialState: T,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, MutableValueState<T>>> = property(
    converter = metaConverter,
    state = MutableValueState(initialState, clock),
    descriptorBuilder = descriptorBuilder,
    nameOverride = nameOverride,
)

/**
 * Registers a property for this device group. The property is specified using the given
 * [DevicePropertySpec] which includes the type converter and descriptor, and a [ValueState] that
 * represents the property's state. Once registered, the property can be dynamically modified or
 * observed.
 *
 * @param T The type of the property value.
 * @param propertySpec Specifies the details of the property, including its converter and descriptor.
 * @param state Represents the current state of the property.
 */
public fun <T, S : ValueState<T>> DeviceConstructor.registerProperty(
    propertySpec: DevicePropertySpec<T>,
    state: S,
): S {
    registerProperty(propertySpec.converter, propertySpec.descriptor, state)
    return state
}