package space.kscience.controls.spec.api

import kotlinx.coroutines.Deferred
import space.kscience.controls.api.*
import space.kscience.controls.spec.ConfigurableCompositeControlComponent
import space.kscience.controls.spec.DeviceActionSpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.MutableDevicePropertySpec
import space.kscience.controls.spec.name
import space.kscience.controls.spec.unit
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Create a [MetaConverter] for enum values using a reified type [E] with an option to ignore case.
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
 * Unified function to declare a device property. If [write] is null, a read-only property is declared;
 * otherwise, a mutable property is declared.
 *
 * @param T The type of the property value.
 * @param D The type of the [ConfigurableCompositeControlComponent] this property belongs to.
 * @param converter The [MetaConverter] for serializing/deserializing the property value.
 * @param name Optional explicit name for the property. If null, the Kotlin property name is used.
 * @param descriptorBuilder Lambda for customizing the [PropertyDescriptor].
 * @param read Suspend function to read the property value from the device.
 * @param write Optional suspend function to write a new value to the property on the device.
 * @return A [PropertyDelegateProvider] for the declared device property.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.typedProperty(
    converter: MetaConverter<T>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(propertyName: String) -> T?,
    write: (suspend D.(propertyName: String, value: T) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> {
    return if (write == null) {
        property(converter, descriptorBuilder, name, read)
    } else {
        mutableProperty(converter, descriptorBuilder, name, read, write)
    }
}

/**
 * Declares a boolean device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.booleanProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Boolean?,
    write: (suspend D.(String, Boolean) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Boolean>>> =
    typedProperty(MetaConverter.boolean, name, descriptorBuilder, read, write)


/**
 * Declares an integer device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.intProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Int?,
    write: (suspend D.(String, Int) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Int>>> =
    typedProperty(MetaConverter.int, name, descriptorBuilder, read, write)

/**
 * Declares a double-precision floating-point device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.doubleProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Double?,
    write: (suspend D.(String, Double) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Double>>> =
    typedProperty(MetaConverter.double, name, descriptorBuilder, read, write)

/**
 * Declares a long integer device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.longProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Long?,
    write: (suspend D.(String, Long) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Long>>> =
    typedProperty(MetaConverter.long, name, descriptorBuilder, read, write)

/**
 * Declares a single-precision floating-point device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.floatProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Float?,
    write: (suspend D.(String, Float) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Float>>> =
    typedProperty(MetaConverter.float, name, descriptorBuilder, read, write)

/**
 * Declares a generic number device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.numberProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Number?,
    write: (suspend D.(String, Number) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Number>>> =
    typedProperty(MetaConverter.number, name, descriptorBuilder, read, write)

/**
 * Declares a string device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.stringProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> String?,
    write: (suspend D.(String, String) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, String>>> =
    typedProperty(MetaConverter.string, name, descriptorBuilder, read, write)

/**
 * Declares a [Meta] device property.
 * See [typedProperty] for parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaProperty(
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> Meta?,
    write: (suspend D.(String, Meta) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, Meta>>> =
    typedProperty(MetaConverter.meta, name, descriptorBuilder, read, write)


/**
 * Declares an enum device property.
 * See [typedProperty] and [createEnumConverter] for parameter details.
 */
public inline fun <reified E : Enum<E>, D : ConfigurableCompositeControlComponent<D>>
        CompositeControlComponentSpec<D>.enumProperty(
    name: String? = null,
    ignoreCase: Boolean = false,
    noinline descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    noinline read: suspend D.(String) -> E?,
    noinline write: (suspend D.(String, E) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, E>>> =
    typedProperty(
        converter = createEnumConverter<E>(ignoreCase),
        name = name,
        descriptorBuilder = descriptorBuilder,
        read = read,
        write = write
    )

/**
 * Declares a list device property.
 *
 * @param T The type of elements in the list.
 * @param listConverter A [MetaConverter] for `List<T>`.
 * See [typedProperty] for other parameter details.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.listProperty(
    listConverter: MetaConverter<List<T>>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(String) -> List<T>?,
    write: (suspend D.(String, List<T>) -> Unit)? = null,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, List<T>>>> =
    typedProperty(listConverter, name, descriptorBuilder, read, write)


/**
 * Declares a logical property that is not directly tied to hardware I/O but managed
 * by the device's internal state (via `getProperty`/`writeProperty` of `Device`).
 *
 * @param T The type of the property value.
 * @param converter The [MetaConverter] for the property type.
 * @param name Optional explicit name for the property.
 * @param descriptorBuilder Lambda for customizing the [PropertyDescriptor].
 * @return A [PropertyDelegateProvider] for the logical property.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.logicalProperty(
    converter: MetaConverter<T>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> = typedProperty(
    converter = converter,
    name = name,
    descriptorBuilder = descriptorBuilder,
    read = { propertyName ->
        this.getProperty(propertyName)?.let(converter::readOrNull)
    },
    write = { propertyName, value ->
        this.writeProperty(propertyName, converter.convert(value))
    }
)

/**
 * Creates an action with specified input and output [MetaConverter]s.
 *
 * @param I The type of the action input.
 * @param O The type of the action output.
 * @param D The type of the [ConfigurableCompositeControlComponent] this action belongs to.
 * @param inputConverter The [MetaConverter] for the action input.
 * @param outputConverter The [MetaConverter] for the action output.
 * @param name Optional explicit name for the action. If null, the Kotlin property name is used.
 * @param descriptorBuilder Lambda for customizing the [ActionDescriptor].
 * @param execute Suspend function defining the action's logic.
 * @return A [PropertyDelegateProvider] for the declared device action.
 */
public fun <I, O, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.typedAction(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.(input: I) -> O,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
    action(
        inputConverter = inputConverter,
        outputConverter = outputConverter,
        descriptorBuilder = descriptorBuilder,
        name = name,
        execute = execute
    )

/**
 * Declares a device action with no parameters and no return value ([Unit]).
 * See [typedAction] for other parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.unitAction(
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.() -> Unit,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
    typedAction(
        inputConverter = MetaConverter.unit,
        outputConverter = MetaConverter.unit,
        name = name,
        descriptorBuilder = descriptorBuilder,
        execute = { execute() }
    )

/**
 * Declares a device action where the execution returns a [Deferred] value. The action itself
 * will await the deferred result.
 * See [typedAction] for other parameter details.
 */
public fun <I, O, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.asyncAction(
    inputConverter: MetaConverter<I>,
    outputConverter: MetaConverter<O>,
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.(input: I) -> Deferred<O>,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
    typedAction(inputConverter, outputConverter, name, descriptorBuilder) { input ->
        execute(input).await()
    }

/**
 * Declares a device action that takes a [Meta] object as input and returns a [Meta] object as output.
 * See [typedAction] for other parameter details.
 */
public fun <D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.metaAction(
    name: String? = null,
    descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
    execute: suspend D.(input: Meta) -> Meta,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
    typedAction(MetaConverter.meta, MetaConverter.meta, name, descriptorBuilder, execute)

/**
 * Validates that the given [device] instance correctly implements all properties and actions
 * defined by this [CompositeDeviceSpec] (or more generally, any [CompositeDeviceSpec]).
 * It checks for the presence of corresponding descriptors in the device.
 *
 * @param device The [Device] instance to validate against this specification.
 * @throws IllegalStateException if a property or action defined in the spec is not found in the device.
 */
public fun CompositeDeviceSpec<*>.validateSpec(device: Device) {
    properties.values.forEach { propSpec ->
        check(device.propertyDescriptors.contains(propSpec.descriptor)) {
            "Property descriptor for '${propSpec.name}' defined in spec is not registered (or mismatch) in device '${device.id}'."
        }
    }
    actions.values.forEach { actSpec ->
        check(device.actionDescriptors.contains(actSpec.descriptor)) {
            "Action descriptor for '${actSpec.name}' defined in spec is not registered (or mismatch) in device '${device.id}'."
        }
    }
}

/**
 * A read-only property with the ability to embed processing logic before and after reading a value.
 *
 * @param T The type of the property value.
 * @param D The type of the [ConfigurableCompositeControlComponent].
 * @param converter The [MetaConverter] for the property type.
 * @param name Optional explicit name for the property.
 * @param descriptorBuilder Lambda for customizing the [PropertyDescriptor].
 * @param beforeRead Suspend lambda executed before the actual read operation.
 * @param afterRead Suspend lambda executed after the read operation, receiving the read value.
 * @param read Suspend function to perform the actual read from the device.
 * @return A [PropertyDelegateProvider] for the checked read-only property.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.checkedReadOnlyProperty(
    converter: MetaConverter<T>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    beforeRead: suspend D.(propertyName: String) -> Unit = { _ -> }, // Default no-op
    afterRead: suspend D.(propertyName: String, value: T?) -> Unit = { _, _ -> }, // Default no-op
    read: suspend D.(propertyName: String) -> T?,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> {
    return property(
        converter = converter,
        descriptorBuilder = descriptorBuilder,
        name = name
    ) { propertyName ->
        beforeRead(this, propertyName) // `this` is D
        val result = read(propertyName)
        afterRead(this, propertyName, result)
        result
    }
}

/**
 * A mutable property with the ability to embed processing logic before and after reading or writing a value.
 *
 * @param T The type of the property value.
 * @param D The type of the [ConfigurableCompositeControlComponent].
 * @param converter The [MetaConverter] for the property type.
 * @param name Optional explicit name for the property.
 * @param descriptorBuilder Lambda for customizing the [PropertyDescriptor].
 * @param beforeRead Suspend lambda executed before a read operation.
 * @param afterRead Suspend lambda executed after a read operation.
 * @param beforeWrite Suspend lambda executed before a write operation.
 * @param afterWrite Suspend lambda executed after a write operation.
 * @param read Suspend function for the actual read operation.
 * @param write Suspend function for the actual write operation.
 * @return A [PropertyDelegateProvider] for the checked mutable property.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.checkedMutableProperty(
    converter: MetaConverter<T>,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    beforeRead: suspend D.(propertyName: String) -> Unit = { _ -> },
    afterRead: suspend D.(propertyName: String, value: T?) -> Unit = { _, _ -> },
    beforeWrite: suspend D.(propertyName: String, newValue: T) -> Unit = { _, _ -> },
    afterWrite: suspend D.(propertyName: String, newValue: T) -> Unit = { _, _ -> },
    read: suspend D.(propertyName: String) -> T?,
    write: suspend D.(propertyName: String, value: T) -> Unit,
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>> {
    return mutableProperty(
        converter = converter,
        descriptorBuilder = descriptorBuilder,
        name = name,
        read = { propertyName ->
            beforeRead(this, propertyName)
            val result = read(propertyName)
            afterRead(this, propertyName, result)
            result
        },
        write = { propertyName, value ->
            beforeWrite(this, propertyName, value)
            write(propertyName, value)
            afterWrite(this, propertyName, value)
        }
    )
}

/**
 * Declares a device property that returns a [defaultValue] if the actual read operation
 * results in null. Can be read-only or mutable.
 *
 * @param T The type of the property value.
 * @param D The type of the [ConfigurableCompositeControlComponent].
 * @param converter The [MetaConverter] for the property type.
 * @param defaultValue The value to return if the read operation yields null.
 * @param name Optional explicit name for the property.
 * @param descriptorBuilder Lambda for customizing the [PropertyDescriptor].
 * @param read Suspend function for the actual read operation.
 * @param write Optional suspend function for write operations (if mutable).
 * @return A [PropertyDelegateProvider] for the property with a default value.
 */
public fun <T, D : ConfigurableCompositeControlComponent<D>> CompositeControlComponentSpec<D>.defaultValueProperty(
    converter: MetaConverter<T>,
    defaultValue: T,
    name: String? = null,
    descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
    read: suspend D.(propertyName: String) -> T?,
    write: (suspend D.(propertyName: String, value: T) -> Unit)? = null
): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> {
    return if (write == null) {
        property(converter, descriptorBuilder, name) { propertyName ->
            read(propertyName) ?: defaultValue
        }
    } else {
        mutableProperty(
            converter,
            descriptorBuilder,
            name,
            read = { propertyName -> read(propertyName) ?: defaultValue },
            write = { propertyName, value -> write(propertyName, value) }
        )
    }
}