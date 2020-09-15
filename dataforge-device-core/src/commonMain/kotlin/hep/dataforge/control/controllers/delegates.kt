package hep.dataforge.control.controllers

import hep.dataforge.control.base.DeviceProperty
import hep.dataforge.control.base.ReadOnlyDeviceProperty
import hep.dataforge.control.base.asMetaItem
import hep.dataforge.meta.*
import hep.dataforge.meta.transformations.MetaConverter
import hep.dataforge.values.Null
import hep.dataforge.values.double
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public operator fun ReadOnlyDeviceProperty.getValue(thisRef: Any?, property: KProperty<*>): MetaItem<*> =
    value ?: MetaItem.ValueItem(Null)

public operator fun DeviceProperty.setValue(thisRef: Any?, property: KProperty<*>, value: MetaItem<*>) {
    this.value = value
}

public fun <T : Any> ReadOnlyDeviceProperty.convert(metaConverter: MetaConverter<T>): ReadOnlyProperty<Any?, T> {
    return ReadOnlyProperty { thisRef, property ->
        getValue(thisRef, property).let { metaConverter.itemToObject(it) }
    }
}

public fun <T : Any> DeviceProperty.convert(metaConverter: MetaConverter<T>): ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return this@convert.getValue(thisRef, property).let { metaConverter.itemToObject(it) }
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this@convert.setValue(thisRef, property, value.let { metaConverter.objectToMetaItem(it) })
        }
    }
}

public fun ReadOnlyDeviceProperty.double(): ReadOnlyProperty<Any?, Double> = convert(MetaConverter.double)
public fun DeviceProperty.double(): ReadWriteProperty<Any?, Double> = convert(MetaConverter.double)

public fun ReadOnlyDeviceProperty.int(): ReadOnlyProperty<Any?, Int> = convert(MetaConverter.int)
public fun DeviceProperty.int(): ReadWriteProperty<Any?, Int> = convert(MetaConverter.int)

public fun ReadOnlyDeviceProperty.string(): ReadOnlyProperty<Any?, String> = convert(MetaConverter.string)
public fun DeviceProperty.string(): ReadWriteProperty<Any?, String> = convert(MetaConverter.string)

//TODO to be moved to DF
private object DurationConverter : MetaConverter<Duration> {
    override fun itemToObject(item: MetaItem<*>): Duration = when (item) {
        is MetaItem.NodeItem -> {
            val unit: DurationUnit = item.node["unit"].enum<DurationUnit>() ?: DurationUnit.SECONDS
            val value = item.node[Meta.VALUE_KEY].double ?: error("No value present for Duration")
            value.toDuration(unit)
        }
        is MetaItem.ValueItem -> item.value.double.toDuration(DurationUnit.SECONDS)
    }

    override fun objectToMetaItem(obj: Duration): MetaItem<*> = obj.toDouble(DurationUnit.SECONDS).asMetaItem()
}

public val MetaConverter.Companion.duration: MetaConverter<Duration> get() = DurationConverter

public fun ReadOnlyDeviceProperty.duration(): ReadOnlyProperty<Any?, Duration> = convert(DurationConverter)
public fun DeviceProperty.duration(): ReadWriteProperty<Any?, Duration> = convert(DurationConverter)