package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.*
import hep.dataforge.values.Null
import hep.dataforge.values.Value
import hep.dataforge.values.asValue
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private fun <D : DeviceBase> D.provideProperty(): ReadOnlyProperty<D, ReadOnlyDeviceProperty> =
    ReadOnlyProperty { _: D, property: KProperty<*> ->
        val name = property.name
        return@ReadOnlyProperty properties[name]!!
    }

public typealias ReadOnlyPropertyDelegate = ReadOnlyProperty<DeviceBase, ReadOnlyDeviceProperty>

private class ReadOnlyDevicePropertyProvider<D : DeviceBase>(
    val owner: D,
    val default: MetaItem<*>?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem<*>?) -> MetaItem<*>,
) : PropertyDelegateProvider<D, ReadOnlyPropertyDelegate> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): ReadOnlyPropertyDelegate {
        val name = property.name
        owner.newReadOnlyProperty(name, default, descriptorBuilder, getter)
        return owner.provideProperty()
    }
}

public fun DeviceBase.reading(
    default: MetaItem<*>? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
): PropertyDelegateProvider<DeviceBase, ReadOnlyPropertyDelegate> = ReadOnlyDevicePropertyProvider(
    this,
    default,
    descriptorBuilder,
    getter
)

public fun DeviceBase.readingValue(
    default: Value? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Any?,
): PropertyDelegateProvider<DeviceBase, ReadOnlyPropertyDelegate> = ReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItem.ValueItem(it) },
    descriptorBuilder,
    getter = { MetaItem.ValueItem(Value.of(getter())) }
)

public fun DeviceBase.readingNumber(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Number,
): PropertyDelegateProvider<DeviceBase, ReadOnlyPropertyDelegate> = ReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItem.ValueItem(it.asValue()) },
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItem.ValueItem(number.asValue())
    }
)

public fun DeviceBase.readingString(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> String,
): PropertyDelegateProvider<DeviceBase, ReadOnlyPropertyDelegate> = ReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItem.ValueItem(it.asValue()) },
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItem.ValueItem(number.asValue())
    }
)

public fun DeviceBase.readingMeta(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend MetaBuilder.() -> Unit,
): PropertyDelegateProvider<DeviceBase, ReadOnlyPropertyDelegate> = ReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItem.NodeItem(it) },
    descriptorBuilder,
    getter = {
        MetaItem.NodeItem(MetaBuilder().apply { getter() })
    }
)

private fun DeviceBase.provideMutableProperty(): ReadOnlyProperty<DeviceBase, DeviceProperty> =
    ReadOnlyProperty { _: DeviceBase, property: KProperty<*> ->
        val name = property.name
        return@ReadOnlyProperty properties[name] as DeviceProperty
    }

public typealias PropertyDelegate = ReadOnlyProperty<DeviceBase, DeviceProperty>

private class DevicePropertyProvider<D : DeviceBase>(
    val owner: D,
    val default: MetaItem<*>?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    private val setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?,
) : PropertyDelegateProvider<D, PropertyDelegate> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): PropertyDelegate {
        val name = property.name
        owner.newMutableProperty(name, default, descriptorBuilder, getter, setter)
        return owner.provideMutableProperty()
    }
}

public fun DeviceBase.writing(
    default: MetaItem<*>? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem<*>?) -> MetaItem<*>,
    setter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>?,
): PropertyDelegateProvider<DeviceBase, PropertyDelegate> = DevicePropertyProvider(
    this,
    default,
    descriptorBuilder,
    getter,
    setter
)

public fun DeviceBase.writingVirtual(
    default: MetaItem<*>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceBase, PropertyDelegate> = writing(
    default,
    descriptorBuilder,
    getter = { it ?: default },
    setter = { _, newItem -> newItem }
)

public fun DeviceBase.writingVirtual(
    default: Value,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceBase, PropertyDelegate> = writing(
    MetaItem.ValueItem(default),
    descriptorBuilder,
    getter = { it ?: MetaItem.ValueItem(default) },
    setter = { _, newItem -> newItem }
)

public fun <D : DeviceBase> D.writingDouble(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Double) -> Double,
    setter: suspend (oldValue: Double?, newValue: Double) -> Double?,
): PropertyDelegateProvider<D, PropertyDelegate> {
    val innerGetter: suspend (MetaItem<*>?) -> MetaItem<*> = {
        MetaItem.ValueItem(getter(it.double ?: Double.NaN).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>? = { oldValue, newValue ->
        setter(oldValue.double, newValue.double ?: Double.NaN)?.asMetaItem()
    }

    return DevicePropertyProvider(
        this,
        MetaItem.ValueItem(Double.NaN.asValue()),
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}

public fun <D : DeviceBase> D.writingBoolean(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Boolean?) -> Boolean,
    setter: suspend (oldValue: Boolean?, newValue: Boolean) -> Boolean?,
): PropertyDelegateProvider<D, PropertyDelegate> {
    val innerGetter: suspend (MetaItem<*>?) -> MetaItem<*> = {
        MetaItem.ValueItem(getter(it.boolean).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem<*>?, newValue: MetaItem<*>) -> MetaItem<*>? = { oldValue, newValue ->
        setter(oldValue.boolean, newValue.boolean ?: error("Can't convert $newValue to boolean"))?.asValue()
            ?.asMetaItem()
    }

    return DevicePropertyProvider(
        this,
        MetaItem.ValueItem(Null),
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}