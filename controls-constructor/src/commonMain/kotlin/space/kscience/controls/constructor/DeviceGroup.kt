package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import space.kscience.controls.api.*
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
public class DeviceGroup(
    public val deviceManager: DeviceManager,
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


    override val context: Context get() = deviceManager.context

    override val coroutineContext: CoroutineContext by lazy {
        context.newCoroutineContext(
            SupervisorJob(context.coroutineContext[Job]) +
                    CoroutineName("Device $this") +
                    CoroutineExceptionHandler { _, throwable ->
                        launch {
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
    }

    private val _devices = hashMapOf<NameToken, Device>()

    override val devices: Map<NameToken, Device> = _devices

    public fun <D : Device> device(token: NameToken, device: D): D {
        check(_devices[token] == null) { "A child device with name $token already exists" }
        _devices[token] = device
        return device
    }

    private val properties: MutableMap<Name, Property> = hashMapOf()

    public fun property(descriptor: PropertyDescriptor, state: DeviceState<out Any>) {
        val name = descriptor.name.parseAsName()
        require(properties[name] == null) { "Can't add property with name $name. It already exists." }
        properties[name] = Property(state, descriptor)
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

    private val sharedMessageFlow = MutableSharedFlow<DeviceMessage>()

    override val messageFlow: Flow<DeviceMessage>
        get() = sharedMessageFlow

    override suspend fun execute(actionName: String, argument: Meta?): Meta? {
        val action = actions[actionName] ?: error("Action with name $actionName not found")
        return action.invoke(argument)
    }

    @DFExperimental
    override var lifecycleState: DeviceLifecycleState = DeviceLifecycleState.STOPPED
        private set(value) {
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
        lifecycleState = DeviceLifecycleState.STARTING
        super.start()
        devices.values.forEach {
            it.start()
        }
        lifecycleState = DeviceLifecycleState.STARTED
    }

    @OptIn(DFExperimental::class)
    override fun stop() {
        devices.values.forEach {
            it.stop()
        }
        super.stop()
        lifecycleState = DeviceLifecycleState.STOPPED
    }

    public companion object {

    }
}

public fun DeviceManager.deviceGroup(
    name: String = "@group",
    meta: Meta = Meta.EMPTY,
    block: DeviceGroup.() -> Unit,
): DeviceGroup {
    val group = DeviceGroup(this, meta).apply(block)
    install(name, group)
    return group
}

public fun Context.deviceGroup(
    name: String = "@group",
    meta: Meta = Meta.EMPTY,
    block: DeviceGroup.() -> Unit,
): DeviceGroup  = request(DeviceManager).deviceGroup(name, meta, block)

private fun DeviceGroup.getOrCreateGroup(name: Name): DeviceGroup {
    return when (name.length) {
        0 -> this
        1 -> {
            val token = name.first()
            when (val d = devices[token]) {
                null -> device(
                    token,
                    DeviceGroup(deviceManager, meta[token] ?: Meta.EMPTY)
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
public fun <D : Device> DeviceGroup.device(name: Name, device: D): D {
    return when (name.length) {
        0 -> error("Can't use empty name for a child device")
        1 -> device(name.first(), device)
        else -> getOrCreateGroup(name.cutLast()).device(name.tokens.last(), device)
    }
}

public fun <D: Device> DeviceGroup.device(name: String, device: D): D = device(name.parseAsName(), device)

/**
 * Add a device creating intermediate groups if necessary. If device with given [name] already exists, throws an error.
 */
public fun DeviceGroup.device(name: Name, factory: Factory<Device>, deviceMeta: Meta? = null): Device {
    val newDevice = factory.build(deviceManager.context, Laminate(deviceMeta, meta[name]))
    device(name, newDevice)
    return newDevice
}

public fun DeviceGroup.device(
    name: String,
    factory: Factory<Device>,
    metaBuilder: (MutableMeta.() -> Unit)? = null,
): Device = device(name.parseAsName(), factory, metaBuilder?.let { Meta(it) })

/**
 * Create or edit a group with a given [name].
 */
public fun DeviceGroup.deviceGroup(name: Name, block: DeviceGroup.() -> Unit): DeviceGroup =
    getOrCreateGroup(name).apply(block)

public fun DeviceGroup.deviceGroup(name: String, block: DeviceGroup.() -> Unit): DeviceGroup =
    deviceGroup(name.parseAsName(), block)

public fun <T : Any> DeviceGroup.property(
    name: String,
    state: DeviceState<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): DeviceState<T> {
    property(
        PropertyDescriptor(name).apply(descriptorBuilder),
        state
    )
    return state
}

public fun <T : Any> DeviceGroup.mutableProperty(
    name: String,
    state: MutableDeviceState<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): MutableDeviceState<T> {
    property(
        PropertyDescriptor(name).apply(descriptorBuilder),
        state
    )
    return state
}

public fun <T : Any> DeviceGroup.virtualProperty(
    name: String,
    initialValue: T,
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): MutableDeviceState<T> {
    val state = VirtualDeviceState<T>(converter, initialValue)
    return mutableProperty(name, state, descriptorBuilder)
}

/**
 * Create a virtual [MutableDeviceState], but do not register it to a device
 */
@Suppress("UnusedReceiverParameter")
public fun <T : Any> DeviceGroup.standAloneProperty(
    initialValue: T,
    converter: MetaConverter<T>,
): MutableDeviceState<T> = VirtualDeviceState<T>(converter, initialValue)