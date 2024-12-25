package space.kscience.controls.spec

import kotlinx.coroutines.Deferred
import space.kscience.controls.api.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Create a [MetaConverter] for enum values using [reified] type with an option to ignore case.
 */
public inline fun <reified E : Enum<E>> createEnumConverter(ignoreCase: Boolean = false): MetaConverter<E> {
    val allValues = enumValues<E>()
    return object : MetaConverter<E> {
        override val descriptor: MetaDescriptor = MetaDescriptor {
            valueType(ValueType.STRING)
            allowedValues(allValues.map { it.name })
        }

        override fun readOrNull(source: Meta): E? {
            val stringVal = source.value?.string ?: return null
            return allValues.firstOrNull { it.name.equals(stringVal, ignoreCase) }
        }

        override fun convert(obj: E): Meta = Meta(obj.name)
    }
}

/**
 * Unified function: if [write] == null -> read-only property, else -> mutable property.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.typedProperty(
    converter: MetaConverter<T>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> T?,
    write: (suspend D.(String, T) -> Unit)? = null,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, T>>> {
    return if (write == null) {
        property(converter, descriptorBuilder, name, read)
    } else {
        mutableProperty(converter, descriptorBuilder, name, read, write)
    }
}

/**
 * Boolean property: read-only or mutable (if [write] is not null).
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.booleanProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Boolean?,
    write: (suspend D.(String, Boolean) -> Unit)? = null,
) = typedProperty(MetaConverter.boolean, name, descriptorBuilder, read, write)

/**
 * Int property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.intProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Int?,
    write: (suspend D.(String, Int) -> Unit)? = null,
) = typedProperty(MetaConverter.int, name, descriptorBuilder, read, write)

/**
 * Double property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.doubleProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Double?,
    write: (suspend D.(String, Double) -> Unit)? = null,
) = typedProperty(MetaConverter.double, name, descriptorBuilder, read, write)

/**
 * Long property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.longProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Long?,
    write: (suspend D.(String, Long) -> Unit)? = null,
) = typedProperty(MetaConverter.long, name, descriptorBuilder, read, write)

/**
 * Float property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.floatProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Float?,
    write: (suspend D.(String, Float) -> Unit)? = null,
) = typedProperty(MetaConverter.float, name, descriptorBuilder, read, write)

/**
 * Number property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.numberProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Number?,
    write: (suspend D.(String, Number) -> Unit)? = null,
) = typedProperty(MetaConverter.number, name, descriptorBuilder, read, write)

/**
 * String property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.stringProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> String?,
    write: (suspend D.(String, String) -> Unit)? = null,
) = typedProperty(MetaConverter.string, name, descriptorBuilder, read, write)

/**
 * Meta property: read-only or mutable.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Meta?,
    write: (suspend D.(String, Meta) -> Unit)? = null,
) = typedProperty(MetaConverter.meta, name, descriptorBuilder, read, write)


/**
 * Enum property (read-only or mutable).
 * [ignoreCase] controls case sensitivity when reading the enum value from Meta.
 * If [write] is null, the property is read-only; otherwise it's read-write.
 */
public inline fun <reified E : Enum<E>, D : ConfigurableCompositeControlComponent<D>>
        CompositeControlComponentSpec<D>.enumProperty(
    name: String? = null,
    ignoreCase: Boolean = false,
    noinline descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    noinline read: suspend D.(String) -> E?,
    noinline write: (suspend D.(String, E) -> Unit)? = null,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DevicePropertySpec<D, E>>> =
    typedProperty(
        converter = createEnumConverter<E>(ignoreCase),
        name = name,
        descriptorBuilder = descriptorBuilder,
        read = read,
        write = write
    )

/**
 * List property: read-only or mutable.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.listProperty(
    listConverter: MetaConverter<List<T>>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> List<T>?,
    write: (suspend D.(String, List<T>) -> Unit)? = null,
) = typedProperty(listConverter, name, descriptorBuilder, read, write)

/**
 * Logical property (no real hardware I/O).
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.logicalProperty(
    converter: MetaConverter<T>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
) = typedProperty(
    converter = converter,
    name = name,
    descriptorBuilder = descriptorBuilder,
    read = { propertyName -> getProperty(propertyName)?.let(converter::readOrNull) },
    write = { propertyName, value -> writeProperty(propertyName, converter.convert(value)) }
)

/**
 * Creates an action with optional input/output converters.
 */
public fun <I, O, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.typedAction(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.(I) -> O,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, I, O>>> =
    action(
        inputConverter = inputConverter,
        outputConverter = outputConverter,
        descriptorBuilder = descriptorBuilder,
        name = name,
        execute = execute
    )

/**
 * Action with no parameters and no return values.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.unitAction(
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.() -> Unit,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    typedAction(
        inputConverter = MetaConverter.unit,
        outputConverter = MetaConverter.unit,
        name = name,
        descriptorBuilder = descriptorBuilder,
    ) {
        execute()
    }

/**
 * Action with async result. The result is awaited.
 */
public fun <I, O, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.asyncAction(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.(I) -> Deferred<O>,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, I, O>>> =
    typedAction(inputConverter, outputConverter, name, descriptorBuilder) { input ->
        execute(input).await()
    }

/**
 * Action that takes and returns [Meta].
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaAction(
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.(Meta) -> Meta,
): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    typedAction(MetaConverter.meta, MetaConverter.meta, name, descriptorBuilder, execute)

/**
 * Validates that [device] has all properties and actions defined by this spec.
 */
public fun CompositeControlComponentSpec<*>.validateSpec(device: Device) {
    properties.values.forEach { propSpec ->
        check(propSpec.descriptor in device.propertyDescriptors) {
            "Property ${propSpec.descriptor.name} not registered in ${device.id}"
        }
    }
    actions.values.forEach { actSpec ->
        check(actSpec.descriptor in device.actionDescriptors) {
            "Action ${actSpec.descriptor.name} not registered in ${device.id}"
        }
    }
}
