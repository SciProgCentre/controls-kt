package space.kscience.controls.spec

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.*
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import kotlin.jvm.JvmName


/**
 * This API is internal and should not be used in user code
 */
@RequiresOptIn("This API should not be called outside of Device internals")
public annotation class InternalDeviceAPI

/**
 * Specification for a device read-only property
 */
public interface DevicePropertySpec<T> {

    /**
     * Meta item converter for the resulting type
     */
    public val converter: MetaConverter<T>

    /**
     * Property descriptor
     */
    public val descriptor: PropertyDescriptor
}

public val DevicePropertySpec<*>.isReadable: Boolean get() = descriptor.readable

public val DevicePropertySpec<*>.isMutable: Boolean get() = descriptor.mutable

public fun <T> DevicePropertySpec(
    converter: MetaConverter<T>,
    descriptor: PropertyDescriptor
): DevicePropertySpec<T> = object : DevicePropertySpec<T> {
    override val converter: MetaConverter<T> = converter
    override val descriptor: PropertyDescriptor = descriptor
}

/**
 * Property name should be unique in a device
 */
public val DevicePropertySpec<*>.name: String get() = descriptor.name


public interface DeviceActionSpec<I, O> {
    /**
     * Action descriptor
     */
    public val descriptor: ActionDescriptor

    public val inputConverter: MetaConverter<I>

    public val outputConverter: MetaConverter<O>
}

public fun <I, O> DeviceActionSpec(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    descriptor: ActionDescriptor,
): DeviceActionSpec<I, O> = object : DeviceActionSpec<I, O> {
    override val descriptor: ActionDescriptor = descriptor
    override val inputConverter: MetaConverter<I> = inputConverter
    override val outputConverter: MetaConverter<O> = outputConverter
}

/**
 * Action name. Should be unique in the device
 */
public val DeviceActionSpec<*, *>.name: String get() = descriptor.name

public suspend fun <T> Device.read(propertySpec: DevicePropertySpec<T>): T =
    propertySpec.converter.readOrNull(readProperty(propertySpec.name)) ?: error("Property read result is not valid")

@JvmName("readWithContext")
context(device: Device)
public suspend fun <T> read(propertySpec: DevicePropertySpec<T>): T = device.read(propertySpec)

/**
 * Read typed value and update/push event if needed.
 * Return null if property read is not successful or property is undefined.
 */
public suspend fun <T> DeviceBase.readOrNull(propertySpec: DevicePropertySpec<T>): T? {
    check(propertySpec.isReadable) { "Property ${propertySpec.name} is not readable" }
    return readPropertyOrNull(propertySpec.name)?.let(propertySpec.converter::readOrNull)
}

@JvmName("readOrNullWithContext")
context(device: DeviceBase)
public suspend fun <T> readOrNull(propertySpec: DevicePropertySpec<T>): T? = device.readOrNull(propertySpec)

public suspend fun <T> Device.getOrRead(propertySpec: DevicePropertySpec<T>): T {
    check(propertySpec.isReadable) { "Property ${propertySpec.name} is not readable" }
    return propertySpec.converter.read(getOrReadProperty(propertySpec.name))
}

@JvmName("getOrReadWithContext")
context(device: Device)
public suspend fun <T> getOrRead(propertySpec: DevicePropertySpec<T>): T = device.getOrRead(propertySpec)

/**
 * Write typed property state and invalidate logical state
 */
public suspend fun <T> Device.write(propertySpec: DevicePropertySpec<T>, value: T) {
    check(propertySpec.isMutable) { "Property ${propertySpec.name} is not mutable" }
    writeProperty(propertySpec.name, propertySpec.converter.convert(value))
}

@JvmName("writeWithContext")
context(device: Device)
public suspend fun <T> write(propertySpec: DevicePropertySpec<T>, value: T): Unit = device.write(propertySpec, value)

/**
 * Fire and forget variant of property writing. Actual write is performed asynchronously on a [Device] scope
 */
public fun <T> Device.writeAsync(propertySpec: DevicePropertySpec<T>, value: T): Job = launch {
    write(propertySpec, value)
}

/**
 * A type safe discrete of property changes for given property
 */
public fun <T> Device.propertyFlow(spec: DevicePropertySpec<T>): Flow<T> = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == spec.name }
    .mapNotNull { spec.converter.read(it.value) }

/**
 * A type safe property change listener. Uses the device [CoroutineScope].
 */
public fun <T> Device.onPropertyChange(
    spec: DevicePropertySpec<T>,
    scope: CoroutineScope = this,
    callback: suspend PropertyChangedMessage.(T) -> Unit,
): Job = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == spec.name }
    .onEach { change ->
        val newValue = spec.converter.read(change.value)
        if (newValue != null) {
            change.callback(newValue)
        }
    }.launchIn(scope)

/**
 * Call [callback] on initial property value and each value change
 */
public fun <T> Device.useProperty(
    spec: DevicePropertySpec<T>,
    scope: CoroutineScope = this,
    callback: suspend (T) -> Unit,
): Job = scope.launch {
    callback(read(spec))
    messageFlow
        .filterIsInstance<PropertyChangedMessage>()
        .filter { it.property == spec.name }
        .collect { change ->
            val newValue = spec.converter.readOrNull(change.value)
            if (newValue != null) {
                callback(newValue)
            }
        }
}

/**
 * Subscribes to changes of a specified device property and invokes the given callback
 * each time the property updates. The callback receives the value of the property
 * along with the timestamp when the value was obtained.
 *
 * @param spec The specification of the device property to observe.
 * @param scope The CoroutineScope in which the monitoring and callback execution will occur.
 *              Defaults to the scope of the device.
 * @param callback A suspend function invoked whenever the property value changes,
 *                 receiving the updated property value along with its timestamp.
 * @return A Job representing the coroutine monitoring the property changes. The job can
 *         be canceled to stop listening for updates.
 */
public fun <T> Device.usePropertyWithTime(
    spec: DevicePropertySpec<T>,
    scope: CoroutineScope = this,
    callback: suspend (ValueWithTime<T>) -> Unit,
): Job = scope.launch {
    callback(ValueWithTime(read(spec), clock.now()))
    messageFlow
        .filterIsInstance<PropertyChangedMessage>()
        .filter { it.property == spec.name }
        .collect { change ->
            val newValue = spec.converter.readOrNull(change.value)
            if (newValue != null) {
                callback(ValueWithTime(newValue, change.time))
            }
        }
}


/**
 * Reset the logical state of a property
 */
public suspend fun CachingDevice.invalidate(propertySpec: DevicePropertySpec<*>) {
    invalidate(propertySpec.name)
}

/**
 * Execute the action with name according to [actionSpec]
 */
public suspend fun <I, O> Device.execute(
    actionSpec: DeviceActionSpec<I, O>,
    input: I
): O? = execute(actionSpec.name, actionSpec.inputConverter.convert(input))?.let {
    actionSpec.outputConverter.read(it)
}


public suspend fun <O> Device.execute(actionSpec: DeviceActionSpec<Unit, O>): O? =
    execute(actionSpec.name, Meta.EMPTY)?.let {
        actionSpec.outputConverter.read(it)
    }