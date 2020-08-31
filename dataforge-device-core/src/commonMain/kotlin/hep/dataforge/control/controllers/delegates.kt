package hep.dataforge.control.controllers

import hep.dataforge.control.base.DeviceProperty
import hep.dataforge.control.base.ReadOnlyDeviceProperty
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.transformations.MetaConverter
import hep.dataforge.values.Null
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

operator fun ReadOnlyDeviceProperty.getValue(thisRef: Any?, property: KProperty<*>): MetaItem<*> =
    value ?: MetaItem.ValueItem(Null)

operator fun DeviceProperty.setValue(thisRef: Any?, property: KProperty<*>, value: MetaItem<*>) {
    this.value = value
}

fun <T : Any> ReadOnlyDeviceProperty.convert(metaConverter: MetaConverter<T>): ReadOnlyProperty<Any?, T> {
    return object : ReadOnlyProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return this@convert.getValue(thisRef, property).let { metaConverter.itemToObject(it) }
        }
    }
}

fun <T : Any> DeviceProperty.convert(metaConverter: MetaConverter<T>): ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return this@convert.getValue(thisRef, property).let { metaConverter.itemToObject(it) }
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this@convert.setValue(thisRef, property, value.let { metaConverter.objectToMetaItem(it) })
        }
    }
}

fun ReadOnlyDeviceProperty.double() = convert(MetaConverter.double)
fun DeviceProperty.double() = convert(MetaConverter.double)

fun ReadOnlyDeviceProperty.int() = convert(MetaConverter.int)
fun DeviceProperty.int() = convert(MetaConverter.int)

fun ReadOnlyDeviceProperty.string() = convert(MetaConverter.string)
fun DeviceProperty.string() = convert(MetaConverter.string)