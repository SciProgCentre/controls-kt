package space.kscience.controls.spec

import space.kscience.controls.api.*
import space.kscience.controls.unit
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A specification of a device
 */
public interface DeviceSpec {
    public val properties: Map<String, DevicePropertySpec<*>>
    public val actions: Map<String, DeviceActionSpec<*, *>>
}

/**
 * A set of all element descriptors in the device spec
 */
public val DeviceSpec.elementDescriptors: Set<DeviceElementDescriptor>
    get() = properties.values.mapTo(mutableSetOf()) {
        it.descriptor
    } + actions.values.mapTo(mutableSetOf()) {
        it.descriptor
    }

/**
 * Create a device specification from a map of properties and actions
 */
public fun DeviceSpec(
    properties: Map<String, DevicePropertySpec<*>>,
    actions: Map<String, DeviceActionSpec<*, *>> = emptyMap()
): DeviceSpec = object : DeviceSpec {
    override val properties: Map<String, DevicePropertySpec<*>> get() = properties
    override val actions: Map<String, DeviceActionSpec<*, *>> get() = actions

}

/**
 * Create a set of element (property or action) descriptors that are present in the spec, but missing in the device
 */
public fun DeviceSpec.checkMissingElements(device: Device): Set<DeviceElementDescriptor> = buildSet {
    properties.forEach { (name, spec) ->
        val descriptor = device.propertyDescriptors.find { it.name == name }
        //TODO allow more flexible comparison of descriptors so they are not strictly equal
        if (descriptor != spec.descriptor) add(spec.descriptor)
    }
    actions.forEach { (name, spec) ->
        val descriptor = device.actionDescriptors.find { it.name == name }
        if (descriptor != spec.descriptor) add(spec.descriptor)
    }
}

/**
 * Verify if this device adheres to the specification
 */
public fun DeviceSpec.verify(device: Device): Boolean = checkMissingElements(device).isEmpty()


/**
 * A base for [DeviceSpec] implementation
 */
public abstract class AbstractDeviceSpec : DeviceSpec {
    //initializing the metadata property for everyone
    private val _properties = hashMapOf<String, DevicePropertySpec<*>>()
    override val properties: Map<String, DevicePropertySpec<*>> get() = _properties

    private val _actions = HashMap<String, DeviceActionSpec<*, *>>()
    override val actions: Map<String, DeviceActionSpec<*, *>> get() = _actions


    public fun <T, P : DevicePropertySpec<T>> registerProperty(deviceProperty: P): P {
        _properties[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    public fun <T> property(
        converter: MetaConverter<T>,
        name: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    ): PropertyDelegateProvider<DeviceSpec, ReadOnlyProperty<DeviceSpec, DevicePropertySpec<T>>> =
        PropertyDelegateProvider { _: DeviceSpec, property ->
            val propertyName = name ?: property.name

            val descriptor: PropertyDescriptor = PropertyDescriptor(propertyName).apply {
                converter.descriptor?.let { converterDescriptor ->
                    metaDescriptor {
                        from(converterDescriptor)
                    }
                }
                fromSpec(property)
                descriptorBuilder()
            }

            val deviceProperty: DevicePropertySpec<T> = DevicePropertySpec<T>(
                converter = converter,
                descriptor = descriptor,
            )

            registerProperty(deviceProperty)
            ReadOnlyProperty<DeviceSpec, DevicePropertySpec<T>> { _, _ ->
                deviceProperty
            }
        }

    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        name: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    ): PropertyDelegateProvider<DeviceSpec, ReadOnlyProperty<DeviceSpec, DevicePropertySpec<T>>> =
        PropertyDelegateProvider { _: DeviceSpec, property: KProperty<*> ->
            val propertyName = name ?: property.name
            val descriptor: PropertyDescriptor = PropertyDescriptor(
                propertyName,
                mutable = true
            ).apply {
                converter.descriptor?.let { converterDescriptor ->
                    metaDescriptor {
                        from(converterDescriptor)
                    }
                }
                fromSpec(property)
                descriptorBuilder()
            }

            val deviceProperty = DevicePropertySpec<T>(
                converter = converter,
                descriptor = descriptor,
            )

            registerProperty(deviceProperty)
            ReadOnlyProperty<DeviceSpec, DevicePropertySpec<T>> { _, _ ->
                deviceProperty
            }
        }


    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<I, O>): DeviceActionSpec<I, O> {
        _actions[deviceAction.name] = deviceAction
        return deviceAction
    }

    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        name: String? = null,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
    ): PropertyDelegateProvider<DeviceSpec, ReadOnlyProperty<DeviceSpec, DeviceActionSpec<I, O>>> =
        PropertyDelegateProvider { _: DeviceSpec, property: KProperty<*> ->
            val actionName = name ?: property.name

            val descriptor: ActionDescriptor = ActionDescriptor(actionName).apply {

                inputConverter.descriptor?.let { converterDescriptor ->
                    inputMetaDescriptor = MetaDescriptor {
                        from(converterDescriptor)
                        from(inputMetaDescriptor)
                    }
                }
                outputConverter.descriptor?.let { converterDescriptor ->
                    outputMetaDescriptor = MetaDescriptor {
                        from(converterDescriptor)
                        from(outputMetaDescriptor)
                    }
                }

                descriptorBuilder()
            }


            val deviceAction = DeviceActionSpec(
                inputConverter = inputConverter,
                outputConverter = outputConverter,
                descriptor = descriptor,
            )

            registerAction(deviceAction)

            ReadOnlyProperty<DeviceSpec, DeviceActionSpec<I, O>> { _, _ ->
                deviceAction
            }
        }
}

/**
 * An action that takes no parameters and returns no values
 */
public fun AbstractDeviceSpec.unitAction(
    name: String? = null,
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceSpec, ReadOnlyProperty<DeviceSpec, DeviceActionSpec<Unit, Unit>>> = action(
    MetaConverter.unit,
    MetaConverter.unit,
    name,
    descriptorBuilder
)

/**
 * An action that takes [Meta] and returns [Meta]. No conversions are done
 */
public fun AbstractDeviceSpec.metaAction(
    name: String? = null,
    descriptorBuilder: ActionDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceSpec, ReadOnlyProperty<DeviceSpec, DeviceActionSpec<Meta, Meta>>> = action(
    MetaConverter.meta,
    MetaConverter.meta,
    name,
    descriptorBuilder
)