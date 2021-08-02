package ru.mipt.npm.controls.properties

import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.meta.transformations.nullableMetaToObject
import space.kscience.dataforge.meta.transformations.nullableObjectToMeta


/**
 * This API is internal and should not be used in user code
 */
@RequiresOptIn
public annotation class InternalDeviceAPI

//TODO relax T restriction after DF 0.4.4
public interface DevicePropertySpec<in D : Device, T : Any> {
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
public suspend fun <D : Device, T : Any> DevicePropertySpec<D, T>.readItem(device: D): Meta =
    converter.objectToMeta(read(device))


public interface WritableDevicePropertySpec<in D : Device, T : Any> : DevicePropertySpec<D, T> {
    /**
     * Write physical value to a device
     */
    @InternalDeviceAPI
    public suspend fun write(device: D, value: T)
}

@OptIn(InternalDeviceAPI::class)
public suspend fun <D : Device, T : Any> WritableDevicePropertySpec<D, T>.writeItem(device: D, item: Meta) {
    write(device, converter.metaToObject(item) ?: error("Meta $item could not be read with $converter"))
}

public interface DeviceActionSpec<in D : Device, I : Any, O : Any> {
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

public suspend fun <D : Device, I : Any, O : Any> DeviceActionSpec<D, I, O>.executeItem(
    device: D,
    item: Meta?
): Meta? {
    val arg = inputConverter.nullableMetaToObject(item)
    val res = execute(device, arg)
    return outputConverter.nullableObjectToMeta(res)
}