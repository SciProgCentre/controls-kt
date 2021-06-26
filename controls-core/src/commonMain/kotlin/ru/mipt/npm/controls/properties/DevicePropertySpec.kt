package ru.mipt.npm.controls.properties

import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.MetaItem
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.meta.transformations.nullableItemToObject
import space.kscience.dataforge.meta.transformations.nullableObjectToMetaItem


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
public suspend fun <D : Device, T : Any> DevicePropertySpec<D, T>.readItem(device: D): MetaItem =
    converter.objectToMetaItem(read(device))


public interface WritableDevicePropertySpec<in D : Device, T : Any> : DevicePropertySpec<D, T> {
    /**
     * Write physical value to a device
     */
    @InternalDeviceAPI
    public suspend fun write(device: D, value: T)
}

@OptIn(InternalDeviceAPI::class)
public suspend fun <D : Device, T : Any> WritableDevicePropertySpec<D, T>.writeItem(device: D, item: MetaItem) {
    write(device, converter.itemToObject(item))
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
    item: MetaItem?
): MetaItem? {
    val arg = inputConverter.nullableItemToObject(item)
    val res = execute(device, arg)
    return outputConverter.nullableObjectToMetaItem(res)
}