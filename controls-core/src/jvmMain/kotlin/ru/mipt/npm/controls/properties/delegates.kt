package ru.mipt.npm.controls.controllers

import kotlinx.coroutines.runBlocking
import ru.mipt.npm.controls.base.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * Blocking read of the value
 */
public operator fun ReadOnlyDeviceProperty.getValue(thisRef: Any?, property: KProperty<*>): Meta =
    runBlocking(scope.coroutineContext) {
        read()
    }

public operator fun <T: Any> TypedReadOnlyDeviceProperty<T>.getValue(thisRef: Any?, property: KProperty<*>): T =
    runBlocking(scope.coroutineContext) {
        readTyped()
    }

public operator fun DeviceProperty.setValue(thisRef: Any?, property: KProperty<*>, value: Meta) {
    this.value = value
}

public operator fun  <T: Any> TypedDeviceProperty<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.typedValue = value
}

public fun <T : Any> ReadOnlyDeviceProperty.convert(
    metaConverter: MetaConverter<T>,
    forceRead: Boolean,
): ReadOnlyProperty<Any?, T> {
    return ReadOnlyProperty { _, _ ->
        runBlocking(scope.coroutineContext) {
            val meta = read(forceRead)
            metaConverter.metaToObject(meta)?: error("Meta $meta could not be converted by $metaConverter")
        }
    }
}

public fun <T : Any> DeviceProperty.convert(
    metaConverter: MetaConverter<T>,
    forceRead: Boolean,
): ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = runBlocking(scope.coroutineContext) {
            val meta = read(forceRead)
            metaConverter.metaToObject(meta)?: error("Meta $meta could not be converted by $metaConverter")
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this@convert.setValue(thisRef, property, value.let { metaConverter.objectToMeta(it) })
        }
    }
}

public fun ReadOnlyDeviceProperty.double(forceRead: Boolean = false): ReadOnlyProperty<Any?, Double> =
    convert(MetaConverter.double, forceRead)

public fun DeviceProperty.double(forceRead: Boolean = false): ReadWriteProperty<Any?, Double> =
    convert(MetaConverter.double, forceRead)

public fun ReadOnlyDeviceProperty.int(forceRead: Boolean = false): ReadOnlyProperty<Any?, Int> =
    convert(MetaConverter.int, forceRead)

public fun DeviceProperty.int(forceRead: Boolean = false): ReadWriteProperty<Any?, Int> =
    convert(MetaConverter.int, forceRead)

public fun ReadOnlyDeviceProperty.string(forceRead: Boolean = false): ReadOnlyProperty<Any?, String> =
    convert(MetaConverter.string, forceRead)

public fun DeviceProperty.string(forceRead: Boolean = false): ReadWriteProperty<Any?, String> =
    convert(MetaConverter.string, forceRead)

public fun ReadOnlyDeviceProperty.boolean(forceRead: Boolean = false): ReadOnlyProperty<Any?, Boolean> =
    convert(MetaConverter.boolean, forceRead)

public fun DeviceProperty.boolean(forceRead: Boolean = false): ReadWriteProperty<Any?, Boolean> =
    convert(MetaConverter.boolean, forceRead)

public fun ReadOnlyDeviceProperty.duration(forceRead: Boolean = false): ReadOnlyProperty<Any?, Duration> =
    convert(DurationConverter, forceRead)

public fun DeviceProperty.duration(forceRead: Boolean = false): ReadWriteProperty<Any?, Duration> =
    convert(DurationConverter, forceRead)