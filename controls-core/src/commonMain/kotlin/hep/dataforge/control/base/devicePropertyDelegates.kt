package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.*
import hep.dataforge.meta.transformations.MetaConverter
import hep.dataforge.values.Null
import hep.dataforge.values.Value
import hep.dataforge.values.asValue
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private fun <D : DeviceBase> D.provideProperty(name: String): ReadOnlyProperty<D, ReadOnlyDeviceProperty> =
    ReadOnlyProperty { _: D, _: KProperty<*> ->
        return@ReadOnlyProperty properties.getValue(name)
    }

private fun <D : DeviceBase, T : Any> D.provideProperty(
    name: String,
    converter: MetaConverter<T>,
): ReadOnlyProperty<D, TypedReadOnlyDeviceProperty<T>> =
    ReadOnlyProperty { _: D, _: KProperty<*> ->
        return@ReadOnlyProperty TypedReadOnlyDeviceProperty(properties.getValue(name), converter)
    }


public typealias ReadOnlyPropertyDelegate = ReadOnlyProperty<DeviceBase, ReadOnlyDeviceProperty>
public typealias TypedReadOnlyPropertyDelegate<T> = ReadOnlyProperty<DeviceBase, TypedReadOnlyDeviceProperty<T>>

private class ReadOnlyDevicePropertyProvider<D : DeviceBase>(
    val owner: D,
    val default: MetaItem?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem?) -> MetaItem,
) : PropertyDelegateProvider<D, ReadOnlyPropertyDelegate> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): ReadOnlyPropertyDelegate {
        val name = property.name
        owner.createReadOnlyProperty(name, default, descriptorBuilder, getter)
        return owner.provideProperty(name)
    }
}

private class TypedReadOnlyDevicePropertyProvider<D : DeviceBase, T : Any>(
    val owner: D,
    val default: MetaItem?,
    val converter: MetaConverter<T>,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem?) -> MetaItem,
) : PropertyDelegateProvider<D, TypedReadOnlyPropertyDelegate<T>> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): TypedReadOnlyPropertyDelegate<T> {
        val name = property.name
        owner.createReadOnlyProperty(name, default, descriptorBuilder, getter)
        return owner.provideProperty(name, converter)
    }
}

public fun DeviceBase.reading(
    default: MetaItem? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem?) -> MetaItem,
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
    default?.let { MetaItemValue(it) },
    descriptorBuilder,
    getter = { MetaItemValue(Value.of(getter())) }
)

public fun DeviceBase.readingNumber(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Number,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Number>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItemValue(it.asValue()) },
    MetaConverter.number,
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItemValue(number.asValue())
    }
)

public fun DeviceBase.readingDouble(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Double,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Double>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItemValue(it.asValue()) },
    MetaConverter.double,
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItemValue(number.asValue())
    }
)

public fun DeviceBase.readingString(
    default: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> String,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<String>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItemValue(it.asValue()) },
    MetaConverter.string,
    descriptorBuilder,
    getter = {
        val number = getter()
        MetaItemValue(number.asValue())
    }
)

public fun DeviceBase.readingBoolean(
    default: Boolean? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Boolean,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Boolean>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItemValue(it.asValue()) },
    MetaConverter.boolean,
    descriptorBuilder,
    getter = {
        val boolean = getter()
        MetaItemValue(boolean.asValue())
    }
)

public fun DeviceBase.readingMeta(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend MetaBuilder.() -> Unit,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Meta>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { MetaItemNode(it) },
    MetaConverter.meta,
    descriptorBuilder,
    getter = {
        MetaItemNode(MetaBuilder().apply { getter() })
    }
)

private fun DeviceBase.provideMutableProperty(name: String): ReadOnlyProperty<DeviceBase, DeviceProperty> =
    ReadOnlyProperty { _: DeviceBase, _: KProperty<*> ->
        return@ReadOnlyProperty properties[name] as DeviceProperty
    }

private fun <T : Any> DeviceBase.provideMutableProperty(
    name: String,
    converter: MetaConverter<T>,
): ReadOnlyProperty<DeviceBase, TypedDeviceProperty<T>> =
    ReadOnlyProperty { _: DeviceBase, _: KProperty<*> ->
        return@ReadOnlyProperty TypedDeviceProperty(properties[name] as DeviceProperty, converter)
    }

public typealias PropertyDelegate = ReadOnlyProperty<DeviceBase, DeviceProperty>
public typealias TypedPropertyDelegate<T> = ReadOnlyProperty<DeviceBase, TypedDeviceProperty<T>>

private class DevicePropertyProvider<D : DeviceBase>(
    val owner: D,
    val default: MetaItem?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem?) -> MetaItem,
    private val setter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem?,
) : PropertyDelegateProvider<D, PropertyDelegate> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): PropertyDelegate {
        val name = property.name
        owner.createMutableProperty(name, default, descriptorBuilder, getter, setter)
        return owner.provideMutableProperty(name)
    }
}

private class TypedDevicePropertyProvider<D : DeviceBase, T : Any>(
    val owner: D,
    val default: MetaItem?,
    val converter: MetaConverter<T>,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (MetaItem?) -> MetaItem,
    private val setter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem?,
) : PropertyDelegateProvider<D, TypedPropertyDelegate<T>> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): TypedPropertyDelegate<T> {
        val name = property.name
        owner.createMutableProperty(name, default, descriptorBuilder, getter, setter)
        return owner.provideMutableProperty(name, converter)
    }
}

public fun DeviceBase.writing(
    default: MetaItem? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (MetaItem?) -> MetaItem,
    setter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem?,
): PropertyDelegateProvider<DeviceBase, PropertyDelegate> = DevicePropertyProvider(
    this,
    default,
    descriptorBuilder,
    getter,
    setter
)

public fun DeviceBase.writingVirtual(
    default: MetaItem,
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
    MetaItemValue(default),
    descriptorBuilder,
    getter = { it ?: MetaItemValue(default) },
    setter = { _, newItem -> newItem }
)

public fun DeviceBase.writingVirtual(
    default: Meta,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceBase, PropertyDelegate> = writing(
    MetaItemNode(default),
    descriptorBuilder,
    getter = { it ?: MetaItemNode(default) },
    setter = { _, newItem -> newItem }
)

public fun <D : DeviceBase> D.writingDouble(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Double) -> Double,
    setter: suspend (oldValue: Double?, newValue: Double) -> Double?,
): PropertyDelegateProvider<D, TypedPropertyDelegate<Double>> {
    val innerGetter: suspend (MetaItem?) -> MetaItem = {
        MetaItemValue(getter(it.double ?: Double.NaN).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem? = { oldValue, newValue ->
        setter(oldValue.double, newValue.double ?: Double.NaN)?.asMetaItem()
    }

    return TypedDevicePropertyProvider(
        this,
        MetaItemValue(Double.NaN.asValue()),
        MetaConverter.double,
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}

public fun <D : DeviceBase> D.writingBoolean(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Boolean?) -> Boolean,
    setter: suspend (oldValue: Boolean?, newValue: Boolean) -> Boolean?,
): PropertyDelegateProvider<D, TypedPropertyDelegate<Boolean>> {
    val innerGetter: suspend (MetaItem?) -> MetaItem = {
        MetaItemValue(getter(it.boolean).asValue())
    }

    val innerSetter: suspend (oldValue: MetaItem?, newValue: MetaItem) -> MetaItem? = { oldValue, newValue ->
        setter(oldValue.boolean, newValue.boolean ?: error("Can't convert $newValue to boolean"))?.asValue()
            ?.asMetaItem()
    }

    return TypedDevicePropertyProvider(
        this,
        MetaItemValue(Null),
        MetaConverter.boolean,
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}