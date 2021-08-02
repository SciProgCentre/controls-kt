package ru.mipt.npm.controls.base

import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.boolean
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.values.Null
import space.kscience.dataforge.values.Value
import space.kscience.dataforge.values.asValue
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
    val default: Meta?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (Meta?) -> Meta,
) : PropertyDelegateProvider<D, ReadOnlyPropertyDelegate> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): ReadOnlyPropertyDelegate {
        val name = property.name
        owner.createReadOnlyProperty(name, default, descriptorBuilder, getter)
        return owner.provideProperty(name)
    }
}

private class TypedReadOnlyDevicePropertyProvider<D : DeviceBase, T : Any>(
    val owner: D,
    val default: Meta?,
    val converter: MetaConverter<T>,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (Meta?) -> Meta,
) : PropertyDelegateProvider<D, TypedReadOnlyPropertyDelegate<T>> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): TypedReadOnlyPropertyDelegate<T> {
        val name = property.name
        owner.createReadOnlyProperty(name, default, descriptorBuilder, getter)
        return owner.provideProperty(name, converter)
    }
}

public fun DeviceBase.reading(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Meta?) -> Meta,
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
    default?.let { Meta(it) },
    descriptorBuilder,
    getter = { Meta(Value.of(getter())) }
)

public fun DeviceBase.readingNumber(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Number,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Number>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { Meta(it.asValue()) },
    MetaConverter.number,
    descriptorBuilder,
    getter = {
        val number = getter()
        Meta(number.asValue())
    }
)

public fun DeviceBase.readingDouble(
    default: Number? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Double,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Double>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { Meta(it.asValue()) },
    MetaConverter.double,
    descriptorBuilder,
    getter = {
        val number = getter()
        Meta(number.asValue())
    }
)

public fun DeviceBase.readingString(
    default: String? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> String,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<String>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { Meta(it.asValue()) },
    MetaConverter.string,
    descriptorBuilder,
    getter = {
        val number = getter()
        Meta(number.asValue())
    }
)

public fun DeviceBase.readingBoolean(
    default: Boolean? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend () -> Boolean,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Boolean>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default?.let { Meta(it.asValue()) },
    MetaConverter.boolean,
    descriptorBuilder,
    getter = {
        val boolean = getter()
        Meta(boolean.asValue())
    }
)

public fun DeviceBase.readingMeta(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend MutableMeta.() -> Unit,
): PropertyDelegateProvider<DeviceBase, TypedReadOnlyPropertyDelegate<Meta>> = TypedReadOnlyDevicePropertyProvider(
    this,
    default,
    MetaConverter.meta,
    descriptorBuilder,
    getter = {
        Meta { getter() }
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
    val default: Meta?,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (Meta?) -> Meta,
    private val setter: suspend (oldValue: Meta?, newValue: Meta) -> Meta?,
) : PropertyDelegateProvider<D, PropertyDelegate> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): PropertyDelegate {
        val name = property.name
        owner.createMutableProperty(name, default, descriptorBuilder, getter, setter)
        return owner.provideMutableProperty(name)
    }
}

private class TypedDevicePropertyProvider<D : DeviceBase, T : Any>(
    val owner: D,
    val default: Meta?,
    val converter: MetaConverter<T>,
    val descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    private val getter: suspend (Meta?) -> Meta,
    private val setter: suspend (oldValue: Meta?, newValue: Meta) -> Meta?,
) : PropertyDelegateProvider<D, TypedPropertyDelegate<T>> {

    override operator fun provideDelegate(thisRef: D, property: KProperty<*>): TypedPropertyDelegate<T> {
        val name = property.name
        owner.createMutableProperty(name, default, descriptorBuilder, getter, setter)
        return owner.provideMutableProperty(name, converter)
    }
}

public fun DeviceBase.writing(
    default: Meta? = null,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Meta?) -> Meta,
    setter: suspend (oldValue: Meta?, newValue: Meta) -> Meta?,
): PropertyDelegateProvider<DeviceBase, PropertyDelegate> = DevicePropertyProvider(
    this,
    default,
    descriptorBuilder,
    getter,
    setter
)

public fun DeviceBase.writingVirtual(
    default: Meta,
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
    Meta(default),
    descriptorBuilder,
    getter = { it ?: Meta(default) },
    setter = { _, newItem -> newItem }
)

public fun <D : DeviceBase> D.writingDouble(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    getter: suspend (Double) -> Double,
    setter: suspend (oldValue: Double?, newValue: Double) -> Double?,
): PropertyDelegateProvider<D, TypedPropertyDelegate<Double>> {
    val innerGetter: suspend (Meta?) -> Meta = {
        Meta(getter(it.double ?: Double.NaN).asValue())
    }

    val innerSetter: suspend (oldValue: Meta?, newValue: Meta) -> Meta? = { oldValue, newValue ->
        setter(oldValue.double, newValue.double ?: Double.NaN)?.asMeta()
    }

    return TypedDevicePropertyProvider(
        this,
        Meta(Double.NaN.asValue()),
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
    val innerGetter: suspend (Meta?) -> Meta = {
        Meta(getter(it.boolean).asValue())
    }

    val innerSetter: suspend (oldValue: Meta?, newValue: Meta) -> Meta? = { oldValue, newValue ->
        setter(oldValue.boolean, newValue.boolean ?: error("Can't convert $newValue to boolean"))?.asValue()
            ?.let { Meta(it) }
    }

    return TypedDevicePropertyProvider(
        this,
        Meta(Null),
        MetaConverter.boolean,
        descriptorBuilder,
        innerGetter,
        innerSetter
    )
}