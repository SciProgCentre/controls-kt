package space.kscience.controls.spec

import kotlinx.coroutines.Deferred
import space.kscience.controls.api.ActionDescriptorBuilder
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptorBuilder
import space.kscience.controls.api.id
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.string
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * Create a [MetaConverter] for enum values
 */
public fun <E : Enum<E>> createEnumConverter(enumValues: Array<E>): MetaConverter<E> = object : MetaConverter<E> {
    override val descriptor: MetaDescriptor = MetaDescriptor {
        valueType(ValueType.STRING)
        allowedValues(enumValues.map { it.name })
    }

    override fun readOrNull(source: Meta): E? {
        val value = source.value ?: return null
        return enumValues.firstOrNull { it.name == value.string }
    }

    override fun convert(obj: E): Meta = Meta(obj.name)
}

/**
 * A read-only device property that delegates reading to a device [KProperty1]
 */
public fun <T, D : Device> CompositeControlComponentSpec<D>.property(
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> T?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, T>>> {
    return  property(converter, descriptorBuilder, name, read)
}

/**
 * Mutable property that delegates reading and writing to a device [KMutableProperty1]
 */
public fun <T, D : Device> CompositeControlComponentSpec<D>.mutableProperty(
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> T?,
    write: suspend D.(propertyName: String, value: T) -> Unit,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, T>>> {
    return  mutableProperty(converter, descriptorBuilder, name, read, write)
}

/**
 * Register a mutable logical property (without a corresponding physical state) for a device
 */
public fun <T, D : DeviceBase<D>> CompositeControlComponentSpec<D>.logical(
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, T>>> =
    mutableProperty(
        converter,
        descriptorBuilder,
        name,
        read = { propertyName -> getProperty(propertyName)?.let(converter::readOrNull) },
        write = { propertyName, value -> writeProperty(propertyName, converter.convert(value)) }
    )

/**
 * Creates a boolean property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.boolean(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Boolean?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Boolean>>> =
    property(MetaConverter.boolean, descriptorBuilder, name, read)

/**
 * Creates a mutable boolean property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.booleanMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Boolean?,
    write: suspend D.(propertyName: String, value: Boolean) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Boolean>>> =
    mutableProperty(MetaConverter.boolean, descriptorBuilder, name, read, write)

/**
 * Creates a read-only number property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.number(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Number?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Number>>> =
    property(MetaConverter.number, descriptorBuilder, name, read)

/**
 * Creates a mutable number property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.numberMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Number?,
    write: suspend D.(propertyName: String, value: Number) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Number>>> =
    mutableProperty(MetaConverter.number, descriptorBuilder, name, read, write)


/**
 * Creates a read-only double property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.double(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Double?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Double>>> =
    property(MetaConverter.double, descriptorBuilder, name, read)

/**
 * Creates a mutable double property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.doubleMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Double?,
    write: suspend D.(propertyName: String, value: Double) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Double>>> =
    mutableProperty(MetaConverter.double, descriptorBuilder, name, read, write)

/**
 * Creates a read-only string property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.string(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> String?
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, String>>> =
    property(MetaConverter.string, descriptorBuilder, name, read)

/**
 * Creates a mutable string property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.stringMutableProperty(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> String?,
    write: suspend D.(propertyName: String, value: String) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, String>>> =
    mutableProperty(MetaConverter.string, descriptorBuilder, name, read, write)

/**
 * Creates a read-only meta property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.meta(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Meta?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Meta>>> =
    property(MetaConverter.meta, descriptorBuilder, name, read)

/**
 * Creates a mutable meta property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.metaMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Meta?,
    write: suspend D.(propertyName: String, value: Meta) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Meta>>> =
    mutableProperty(MetaConverter.meta, descriptorBuilder, name, read, write)

/**
 * Creates a read-only enum property for a device.
 */
public fun <E : Enum<E>, D : Device> CompositeControlComponentSpec<D>.enum(
    enumValues: Array<E>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> E?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, E>>> {
    val converter = createEnumConverter(enumValues)
    return property(converter, descriptorBuilder, name, read)
}

/**
 * Creates a mutable enum property for a device.
 */
public fun <E : Enum<E>, D : Device> CompositeControlComponentSpec<D>.enumMutable(
    enumValues: Array<E>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> E?,
    write: suspend D.(propertyName: String, value: E) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, E>>> {
    val converter = createEnumConverter(enumValues)
    return mutableProperty(converter, descriptorBuilder, name, read, write)
}

/**
 * Creates a read-only float property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.float(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Float?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Float>>> =
    property(MetaConverter.float, descriptorBuilder, name, read)

/**
 * Creates a mutable float property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.floatMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Float?,
    write: suspend D.(propertyName: String, value: Float) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Float>>> =
    mutableProperty(MetaConverter.float, descriptorBuilder, name, read, write)

/**
 * Creates a read-only long property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.long(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Long?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Long>>> =
    property(MetaConverter.long, descriptorBuilder, name, read)

/**
 * Creates a mutable long property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.longMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Long?,
    write: suspend D.(propertyName: String, value: Long) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Long>>> =
    mutableProperty(MetaConverter.long, descriptorBuilder, name, read, write)

/**
 * Creates a read-only int property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.int(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Int?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Int>>> =
    property(MetaConverter.int, descriptorBuilder, name, read)

/**
 * Creates a mutable int property for a device.
 */
public fun <D : Device> CompositeControlComponentSpec<D>.intMutable(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Int?,
    write: suspend D.(propertyName: String, value: Int) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Int>>> =
    mutableProperty(MetaConverter.int, descriptorBuilder, name, read, write)

/**
 * Creates a read-only list property for a device.
 */
public fun <T, D : Device> CompositeControlComponentSpec<D>.list(
    converter: MetaConverter<List<T>>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> List<T>?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, List<T>>>> =
    property(converter, descriptorBuilder, name, read)

/**
 * Creates a mutable list property for a device.
 */
public fun <T, D : Device> CompositeControlComponentSpec<D>.listMutable(
    converter: MetaConverter<List<T>>,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> List<T>?,
    write: suspend D.(propertyName: String, value: List<T>) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, List<T>>>> =
    mutableProperty(converter, descriptorBuilder, name, read, write)


public fun <I, O, D : Device> CompositeControlComponentSpec<D>.asyncActionProperty(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(I) -> Deferred<O>,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, I, O>>> =
    action(inputConverter, outputConverter, descriptorBuilder, name) { input ->
        execute(input).await()
    }

public fun <T, D : Device> CompositeControlComponentSpec<D>.metaProperty(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Meta?,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, Meta>>> =
    property(MetaConverter.meta, descriptorBuilder, name, read)


public fun <T, D : Device> CompositeControlComponentSpec<D>.mutableMetaProperty(
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    read: suspend D.(propertyName: String) -> Meta?,
    write: suspend D.(propertyName: String, value: Meta) -> Unit
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, MutableDevicePropertySpec<D, Meta>>> =
    mutableProperty(MetaConverter.meta, descriptorBuilder, name, read, write)

/**
 * An action that takes no parameters and returns no values
 */
public fun <T, D : Device> CompositeControlComponentSpec<D>.unitAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.() -> Unit,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    action(
        MetaConverter.unit,
        MetaConverter.unit,
        descriptorBuilder,
        name
    ) {
        execute()
    }

public fun <I, O, D : Device> CompositeControlComponentSpec<D>.asyncAction(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(I) -> Deferred<O>,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, I, O>>> =
    action(
        inputConverter,
        outputConverter,
        descriptorBuilder,
        name
    ) {
        execute(it).await()
    }

/**
 * An action that takes [Meta] and returns [Meta]. No conversions are done
 */
public fun <T, D : Device> CompositeControlComponentSpec<D>.metaAction(
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    name: String? = null,
    execute: suspend D.(Meta) -> Meta,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    action(
        MetaConverter.meta,
        MetaConverter.meta,
        descriptorBuilder,
        name
    ) {
        execute(it)
    }

/**
 * Throw an exception if device does not have all properties and actions defined by this specification
 */
public fun CompositeControlComponentSpec<*>.validate(device: Device) {
    properties.map { it.value.descriptor }.forEach { specProperty ->
        check(specProperty in device.propertyDescriptors) { "Property ${specProperty.name} not registered in ${device.id}" }
    }

    actions.map { it.value.descriptor }.forEach { specAction ->
        check(specAction in device.actionDescriptors) { "Action ${specAction.name} not registered in ${device.id}" }
    }
}