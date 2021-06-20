package ru.mipt.npm.controls.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.kscience.dataforge.meta.MetaItem
import space.kscience.dataforge.meta.transformations.MetaConverter

/**
 * A type-safe wrapper on top of read-only property
 */
public open class TypedReadOnlyDeviceProperty<T : Any>(
    private val property: ReadOnlyDeviceProperty,
    protected val converter: MetaConverter<T>,
) : ReadOnlyDeviceProperty by property {

    public fun updateLogical(obj: T) {
        property.updateLogical(converter.objectToMetaItem(obj))
    }

    public open val typedValue: T? get() = value?.let { converter.itemToObject(it) }

    public suspend fun readTyped(force: Boolean = false): T = converter.itemToObject(read(force))

    public fun flowTyped(): Flow<T?> = flow().map { it?.let { converter.itemToObject(it) } }
}

/**
 * A type-safe wrapper for a read-write device property
 */
public class TypedDeviceProperty<T : Any>(
    private val property: DeviceProperty,
    converter: MetaConverter<T>,
) : TypedReadOnlyDeviceProperty<T>(property, converter), DeviceProperty {

    override var value: MetaItem?
        get() = property.value
        set(arg) {
            property.value = arg
        }

    public override var typedValue: T?
        get() = value?.let { converter.itemToObject(it) }
        set(arg) {
            property.value = arg?.let { converter.objectToMetaItem(arg) }
        }

    override suspend fun write(item: MetaItem) {
        property.write(item)
    }

    public suspend fun write(obj: T) {
        property.write(converter.objectToMetaItem(obj))
    }
}