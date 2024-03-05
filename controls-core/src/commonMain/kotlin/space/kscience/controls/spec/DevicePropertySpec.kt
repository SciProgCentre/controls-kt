package space.kscience.controls.spec

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.kscience.controls.api.*
import space.kscience.dataforge.meta.MetaConverter


/**
 * This API is internal and should not be used in user code
 */
@RequiresOptIn("This API should not be called outside of Device internals")
public annotation class InternalDeviceAPI

/**
 * Specification for a device read-only property
 */
public interface DevicePropertySpec<in D, T> {
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


public interface MutableDevicePropertySpec<in D : Device, T> : DevicePropertySpec<D, T> {
    /**
     * Write physical value to a device
     */
    @InternalDeviceAPI
    public suspend fun write(device: D, value: T)

}

public interface DeviceActionSpec<in D, I, O> {
    /**
     * Action descriptor
     */
    public val descriptor: ActionDescriptor

    public val inputConverter: MetaConverter<I>

    public val outputConverter: MetaConverter<O>

    /**
     * Execute action on a device
     */
    public suspend fun execute(device: D, input: I): O
}

/**
 * Action name. Should be unique in the device
 */
public val DeviceActionSpec<*, *, *>.name: String get() = descriptor.name

public suspend fun <T, D : Device> D.read(propertySpec: DevicePropertySpec<D, T>): T =
    propertySpec.converter.readOrNull(readProperty(propertySpec.name)) ?: error("Property read result is not valid")

/**
 * Read typed value and update/push event if needed.
 * Return null if property read is not successful or property is undefined.
 */
public suspend fun <T, D : DeviceBase<D>> D.readOrNull(propertySpec: DevicePropertySpec<D, T>): T? =
    readPropertyOrNull(propertySpec.name)?.let(propertySpec.converter::readOrNull)

public suspend fun <T, D : Device> D.getOrRead(propertySpec: DevicePropertySpec<D, T>): T =
    propertySpec.converter.read(getOrReadProperty(propertySpec.name))

/**
 * Write typed property state and invalidate logical state
 */
public suspend fun <T, D : Device> D.write(propertySpec: MutableDevicePropertySpec<D, T>, value: T) {
    writeProperty(propertySpec.name, propertySpec.converter.convert(value))
}

/**
 * Fire and forget variant of property writing. Actual write is performed asynchronously on a [Device] scope
 */
public fun <T, D : Device> D.writeAsync(propertySpec: MutableDevicePropertySpec<D, T>, value: T): Job = launch {
    write(propertySpec, value)
}

/**
 * A type safe flow of property changes for given property
 */
public fun <D : Device, T> D.propertyFlow(spec: DevicePropertySpec<D, T>): Flow<T> = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == spec.name }
    .mapNotNull { spec.converter.read(it.value) }

/**
 * A type safe property change listener. Uses the device [CoroutineScope].
 */
public fun <D : Device, T> D.onPropertyChange(
    spec: DevicePropertySpec<D, T>,
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
public fun <D : Device, T> D.useProperty(
    spec: DevicePropertySpec<D, T>,
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
 * Reset the logical state of a property
 */
public suspend fun <D : CachingDevice> D.invalidate(propertySpec: DevicePropertySpec<D, *>) {
    invalidate(propertySpec.name)
}

/**
 * Execute the action with name according to [actionSpec]
 */
public suspend fun <I, O, D : Device> D.execute(actionSpec: DeviceActionSpec<D, I, O>, input: I): O =
    actionSpec.execute(this, input)

public suspend fun <O, D : Device> D.execute(actionSpec: DeviceActionSpec<D, Unit, O>): O =
    actionSpec.execute(this, Unit)