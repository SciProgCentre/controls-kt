package ru.mipt.npm.controls.properties

import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter


/**
 * This API is internal and should not be used in user code
 */
@RequiresOptIn
public annotation class InternalDeviceAPI

public interface DevicePropertySpec<in D : Device, T> {
    /**
     * Property name, should be unique in device
     */
    public val name: String

    /**
     * Property descriptor
     */
    public val descriptor: PropertyDescriptor

    /**
     * Meta item converter for resulting type
     */
    public val converter: MetaConverter<T>

    /**
     * Read physical value from the given [device]
     */
    @InternalDeviceAPI
    public suspend fun read(device: D): T
}

@OptIn(InternalDeviceAPI::class)
public suspend fun <D : Device, T> DevicePropertySpec<D, T>.readMeta(device: D): Meta =
    converter.objectToMeta(read(device))


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
     * Action name, should be unique in device
     */
    public val name: String

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

public suspend fun <D : Device, I, O> DeviceActionSpec<D, I, O>.executeMeta(
    device: D,
    item: Meta?
): Meta? {
    val arg = item?.let { inputConverter.metaToObject(item) }
    val res = execute(device, arg)
    return res?.let { outputConverter.objectToMeta(res) }
}