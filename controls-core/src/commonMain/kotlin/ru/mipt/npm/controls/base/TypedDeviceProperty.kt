package ru.mipt.npm.controls.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter

/**
 * A type-safe wrapper on top of read-only property
 */
public open class TypedReadOnlyDeviceProperty<T : Any>(
    private val property: ReadOnlyDeviceProperty,
    protected val converter: MetaConverter<T>,
) : ReadOnlyDeviceProperty by property {

    public fun updateLogical(obj: T) {
        property.updateLogical(converter.objectToMeta(obj))
    }

    public open val typedValue: T? get() = value?.let { converter.metaToObject(it) }

    public suspend fun readTyped(force: Boolean = false): T {
        val meta = read(force)
        return converter.metaToObject(meta)
            ?: error("Meta $meta could not be converted by $converter")
    }

    public fun flowTyped(): Flow<T?> = flow().map { it?.let { converter.metaToObject(it) } }
}

/**
 * A type-safe wrapper for a read-write device property
 */
public class TypedDeviceProperty<T : Any>(
    private val property: DeviceProperty,
    converter: MetaConverter<T>,
) : TypedReadOnlyDeviceProperty<T>(property, converter), DeviceProperty {

    override var value: Meta?
        get() = property.value
        set(arg) {
            property.value = arg
        }

    public override var typedValue: T?
        get() = value?.let { converter.metaToObject(it) }
        set(arg) {
            property.value = arg?.let { converter.objectToMeta(arg) }
        }

    override suspend fun write(item: Meta) {
        property.write(item)
    }

    public suspend fun write(obj: T) {
        property.write(converter.objectToMeta(obj))
    }
}