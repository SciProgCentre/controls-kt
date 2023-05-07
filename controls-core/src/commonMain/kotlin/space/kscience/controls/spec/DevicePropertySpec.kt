package space.kscience.controls.spec

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter


/**
 * This API is internal and should not be used in user code
 */
@RequiresOptIn("This API should not be called outside of Device internals")
public annotation class InternalDeviceAPI

public interface DevicePropertySpec<in D : Device, T> {
    /**
     * Property descriptor
     */
    public val descriptor: PropertyDescriptor

    /**
     * Meta item converter for the resulting type
     */
    public val converter: MetaConverter<T>

    /**
     * Read physical value from the given [device]
     */
    @InternalDeviceAPI
    public suspend fun read(device: D): T?
}

/**
 * Property name, should be unique in device
 */
public val DevicePropertySpec<*, *>.name: String get() = descriptor.name

@OptIn(InternalDeviceAPI::class)
public suspend fun <D : Device, T> DevicePropertySpec<D, T>.readMeta(device: D): Meta? =
    read(device)?.let(converter::objectToMeta)


public interface WritableDevicePropertySpec<in D : Device, T> : DevicePropertySpec<D, T> {
    /**
     * Write physical value to a device
     */
    @InternalDeviceAPI
    public suspend fun write(device: D, value: T)
}

@OptIn(InternalDeviceAPI::class)
public suspend fun <D : Device, T> WritableDevicePropertySpec<D, T>.writeMeta(device: D, item: Meta) {
    write(device, converter.metaToObject(item) ?: error("Meta $item could not be read with $converter"))
}

public interface DeviceActionSpec<in D : Device, I, O> {
    /**
     * Action descriptor
     */
    public val descriptor: ActionDescriptor

    public val inputConverter: MetaConverter<I>

    public val outputConverter: MetaConverter<O>

    /**
     * Execute action on a device
     */
    public suspend fun execute(device: D, input: I?): O?
}

/**
 * Action name. Should be unique in the device
 */
public val DeviceActionSpec<*, *, *>.name: String get() = descriptor.name

public suspend fun <D : Device, I, O> DeviceActionSpec<D, I, O>.executeWithMeta(
    device: D,
    item: Meta?,
): Meta? {
    val arg = item?.let { inputConverter.metaToObject(item) }
    val res = execute(device, arg)
    return res?.let { outputConverter.objectToMeta(res) }
}

/**
 * Read typed value and update/push event if needed.
 * Return null if property read is not successful or property is undefined.
 */
@OptIn(InternalDeviceAPI::class)
public suspend fun <T, D : Device> D.readOrNull(propertySpec: DevicePropertySpec<D, T>): T? {
    val res = propertySpec.read(this) ?: return null
    @Suppress("UNCHECKED_CAST")
    (this as? DeviceBase<D>)?.updateLogical(propertySpec, res)
    return res
}

public suspend fun <T, D : Device> D.read(propertySpec: DevicePropertySpec<D, T>): T =
    readOrNull(propertySpec) ?: error("Failed to read property ${propertySpec.name} state")

public operator fun <T, D : Device> D.get(propertySpec: DevicePropertySpec<D, T>): T? =
    getProperty(propertySpec.name)?.let(propertySpec.converter::metaToObject)

/**
 * Write typed property state and invalidate logical state
 */
@OptIn(InternalDeviceAPI::class)
public suspend fun <T, D : Device> D.write(propertySpec: WritableDevicePropertySpec<D, T>, value: T) {
    invalidate(propertySpec.name)
    propertySpec.write(this, value)
    //perform asynchronous read and update after write
    launch {
        read(propertySpec)
    }
}

/**
 * Fire and forget variant of property writing. Actual write is performed asynchronously on a [Device] scope
 */
public operator fun <T, D : Device> D.set(propertySpec: WritableDevicePropertySpec<D, T>, value: T): Unit {
    launch {
        write(propertySpec, value)
    }
}

/**
 * Reset the logical state of a property
 */
public suspend fun <D : Device> D.invalidate(propertySpec: DevicePropertySpec<D, *>) {
    invalidate(propertySpec.name)
}

public suspend fun <I, O, D : Device> D.execute(actionSpec: DeviceActionSpec<D, I, O>, input: I? = null): O? =
    actionSpec.execute(this, input)

/**
 * A type safe property change listener
 */
public fun <D : Device, T> Device.onPropertyChange(
    spec: DevicePropertySpec<D, T>,
    callback: suspend PropertyChangedMessage.(T?) -> Unit,
): Job = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == spec.name }
    .onEach { change ->
        change.callback(spec.converter.metaToObject(change.value))
    }.launchIn(this)