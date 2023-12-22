package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.api.*
import space.kscience.controls.api.DeviceLifecycleState.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Laminate
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.*
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext


/**
 * A mutable group of devices and properties to be used for lightweight design and simulations.
 */
public open class DeviceGroup(
    final override val context: Context,
    override val meta: Meta,
) : DeviceHub, CachingDevice {

    internal class Property(
        val state: DeviceState<out Any>,
        val descriptor: PropertyDescriptor,
    )

    internal class Action(
        val invoke: suspend (Meta?) -> Meta?,
        val descriptor: ActionDescriptor,
    )


    private val sharedMessageFlow = MutableSharedFlow<DeviceMessage>()

    override val messageFlow: Flow<DeviceMessage>
        get() = sharedMessageFlow

    override val coroutineContext: CoroutineContext = context.newCoroutineContext(
        SupervisorJob(context.coroutineContext[Job]) +
                CoroutineName("Device $this") +
                CoroutineExceptionHandler { _, throwable ->
                    context.launch {
                        sharedMessageFlow.emit(
                            DeviceErrorMessage(
                                errorMessage = throwable.message,
                                errorType = throwable::class.simpleName,
                                errorStackTrace = throwable.stackTraceToString()
                            )
                        )
                    }
                }
    )


    private val _devices = hashMapOf<NameToken, Device>()

    override val devices: Map<NameToken, Device> = _devices

    /**
     * Register and initialize (synchronize child's lifecycle state with group state) a new device in this group
     */
    @OptIn(DFExperimental::class)
    public fun <D : Device> install(token: NameToken, device: D): D {
        require(_devices[token] == null) { "A child device with name $token already exists" }
        //start the child device if needed
        if(lifecycleState == STARTED || lifecycleState == STARTING) launch { device.start() }
        _devices[token] = device
        return device
    }

    private val properties: MutableMap<Name, Property> = hashMapOf()

    /**
     * Register a new property based on [DeviceState]. Properties could be modified dynamically
     */
    public fun registerProperty(descriptor: PropertyDescriptor, state: DeviceState<out Any>) {
        val name = descriptor.name.parseAsName()
        require(properties[name] == null) { "Can't add property with name $name. It already exists." }
        properties[name] = Property(state, descriptor)
        state.metaFlow.onEach {
            sharedMessageFlow.emit(
                PropertyChangedMessage(
                    descriptor.name,
                    it
                )
            )
        }.launchIn(this)
    }

    private val actions: MutableMap<Name, Action> = hashMapOf()

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }

    override suspend fun readProperty(propertyName: String): Meta =
        properties[propertyName.parseAsName()]?.state?.valueAsMeta
            ?: error("Property with name $propertyName not found")

    override fun getProperty(propertyName: String): Meta? = properties[propertyName.parseAsName()]?.state?.valueAsMeta

    override suspend fun invalidate(propertyName: String) {
        //does nothing for this implementation
    }

    override suspend fun writeProperty(propertyName: String, value: Meta) {
        val property = (properties[propertyName.parseAsName()]?.state as? MutableDeviceState)
            ?: error("Property with name $propertyName not found")
        property.valueAsMeta = value
    }


    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val action = actions[actionName] ?: error("Action with name $actionName not found")
        return action.invoke(argument)
    }

    @DFExperimental
    override var lifecycleState: DeviceLifecycleState = STOPPED
        protected set(value) {
            if (field != value) {
                launch {
                    sharedMessageFlow.emit(
                        DeviceLifeCycleMessage(value)
                    )
                }
            }
            field = value
        }


    @OptIn(DFExperimental::class)
    override suspend fun start() {
        lifecycleState = STARTING
        super.start()
        devices.values.forEach {
            it.start()
        }
        lifecycleState = STARTED
    }

    @OptIn(DFExperimental::class)
    override fun stop() {
        devices.values.forEach {
            it.stop()
        }
        super.stop()
        lifecycleState = STOPPED
    }

    public companion object {

    }
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

private fun DeviceGroup.getOrCreateGroup(name: Name): DeviceGroup {
    return when (name.length) {
        0 -> this
        1 -> {
            val token = name.first()
            when (val d = devices[token]) {
                null -> install(
                    token,
                    DeviceGroup(context, meta[token] ?: Meta.EMPTY)
                )

                else -> (d as? DeviceGroup) ?: error("Device $name is not a DeviceGroup")
            }
        }

        else -> getOrCreateGroup(name.first().asName()).getOrCreateGroup(name.cutFirst())
    }
}

/**
 * Register a device at given [name] path
 */
public fun <D : Device> DeviceGroup.install(name: Name, device: D): D {
    return when (name.length) {
        0 -> error("Can't use empty name for a child device")
        1 -> install(name.first(), device)
        else -> getOrCreateGroup(name.cutLast()).install(name.tokens.last(), device)
    }
}

public fun <D : Device> DeviceGroup.install(name: String, device: D): D =
    install(name.parseAsName(), device)

public fun <D : Device> Context.install(name: String, device: D): D = request(DeviceManager).install(name, device)

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
    getOrCreateGroup(name).apply(block)

public fun DeviceGroup.registerDeviceGroup(name: String, block: DeviceGroup.() -> Unit): DeviceGroup =
    registerDeviceGroup(name.parseAsName(), block)

/**
 * Register read-only property based on [state]
 */
public fun <T : Any> DeviceGroup.registerProperty(
    name: String,
    state: DeviceState<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
) {
    registerProperty(
        PropertyDescriptor(name).apply(descriptorBuilder),
        state
    )
}

/**
 * Register a mutable property based on mutable [state]
 */
public fun <T : Any> DeviceGroup.registerMutableProperty(
    name: String,
    state: MutableDeviceState<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
) {
    registerProperty(
        PropertyDescriptor(name).apply(descriptorBuilder),
        state
    )
}


/**
 * Create a virtual [MutableDeviceState], but do not register it to a device
 */
@Suppress("UnusedReceiverParameter")
public fun <T : Any> DeviceGroup.state(
    converter: MetaConverter<T>,
    initialValue: T,
): MutableDeviceState<T> = VirtualDeviceState<T>(converter, initialValue)

/**
 * Create a new virtual mutable state and a property based on it.
 * @return the mutable state used in property
 */
public fun <T : Any> DeviceGroup.registerVirtualProperty(
    name: String,
    initialValue: T,
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): MutableDeviceState<T> {
    val state = state(converter, initialValue)
    registerMutableProperty(name, state, descriptorBuilder)
    return state
}
