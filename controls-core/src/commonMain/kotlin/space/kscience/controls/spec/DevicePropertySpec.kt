package space.kscience.controls.spec

import kotlinx.coroutines.CoroutineScope
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
import space.kscience.dataforge.meta.transformations.MetaConverter


/**
 * This API is internal and should not be used in user code
 */
@RequiresOptIn("This API should not be called outside of Device internals")
public annotation class InternalDeviceAPI

/**
 * Specification for a device read-only property
 */
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
 * Property name should be unique in a device
 */
public val DevicePropertySpec<*, *>.name: String get() = descriptor.name


public interface WritableDevicePropertySpec<in D : Device, T> : DevicePropertySpec<D, T> {
    /**
     * Write physical value to a device
     */
    @InternalDeviceAPI
    public suspend fun write(device: D, value: T)

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

public suspend fun <T, D : Device> D.read(propertySpec: DevicePropertySpec<D, T>): T =
    propertySpec.converter.metaToObject(readProperty(propertySpec.name)) ?: error("Property read result is not valid")

/**
 * Read typed value and update/push event if needed.
 * Return null if property read is not successful or property is undefined.
 */
public suspend fun <T, D : DeviceBase<D>> D.readOrNull(propertySpec: DevicePropertySpec<D, T>): T? =
    readPropertyOrNull(propertySpec.name)?.let(propertySpec.converter::metaToObject)


public operator fun <T, D : Device> D.get(propertySpec: DevicePropertySpec<D, T>): T? =
    getProperty(propertySpec.name)?.let(propertySpec.converter::metaToObject)

/**
 * Write typed property state and invalidate logical state
 */
public suspend fun <T, D : Device> D.write(propertySpec: WritableDevicePropertySpec<D, T>, value: T) {
    writeProperty(propertySpec.name, propertySpec.converter.objectToMeta(value))
}

/**
 * Fire and forget variant of property writing. Actual write is performed asynchronously on a [Device] scope
 */
public operator fun <T, D : Device> D.set(propertySpec: WritableDevicePropertySpec<D, T>, value: T): Job = launch {
    write(propertySpec, value)
}

/**
 * A type safe property change listener. Uses the device [CoroutineScope].
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

/**
 * Reset the logical state of a property
 */
public suspend fun <D : Device> D.invalidate(propertySpec: DevicePropertySpec<D, *>) {
    invalidate(propertySpec.name)
}

/**
 * Execute the action with name according to [actionSpec]
 */
public suspend fun <I, O, D : Device> D.execute(actionSpec: DeviceActionSpec<D, I, O>, input: I? = null): O? =
    actionSpec.execute(this, input)