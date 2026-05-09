package space.kscience.controls.constructor

import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * A base for a strongly typed device constructor block. Has additional delegates for type-safe devices
 */
public abstract class DeviceConstructor(
    context: Context,
    meta: Meta = Meta.EMPTY,
) : DeviceGroup(context, meta), MutableConstructor {
    private val _constructorElements: MutableSet<ConstructorElement> = mutableSetOf()
    override val constructorElements: Set<ConstructorElement> get() = _constructorElements

    override fun registerElement(constructorElement: ConstructorElement) {
        _constructorElements.add(constructorElement)
    }

    override fun unregisterElement(constructorElement: ConstructorElement) {
        _constructorElements.remove(constructorElement)
    }

    override fun <T, S : ValueState<T>> registerProperty(
        converter: MetaConverter<T>,
        descriptor: PropertyDescriptor,
        state: S,
    ): S {
        val res = super.registerProperty(converter, descriptor, state)
        registerElement(PropertyConstructorElement(this, descriptor.name, state))
        return res
    }
}

/**
 * Register a device, provided by a given [factory] and
 */
public fun <D : Device> DeviceConstructor.device(
    factory: Factory<D>,
    meta: Meta? = null,
    nameOverride: Name? = null,
    metaLocation: Name? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, D>> =
    PropertyDelegateProvider { _: DeviceConstructor, property: KProperty<*> ->
        val name = nameOverride ?: property.name.asName()
        val device = install(name, factory, meta, metaLocation ?: name)
        ReadOnlyProperty { _: DeviceConstructor, _ ->
            device
        }
    }

public fun <D : Device> DeviceConstructor.device(
    device: D,
    nameOverride: Name? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, D>> =
    PropertyDelegateProvider { _: DeviceConstructor, property: KProperty<*> ->
        val name = nameOverride ?: property.name.asName()
        install(name, device)
        ReadOnlyProperty { _: DeviceConstructor, _ ->
            device
        }
    }

/**
 * Register a property and provide a direct reader for it
 */
public fun <T, S : ValueState<T>> DeviceConstructor.property(
    converter: MetaConverter<T>,
    state: S,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, S>> =
    PropertyDelegateProvider { _: DeviceConstructor, property ->
        val name = nameOverride ?: property.name
        val descriptor = PropertyDescriptor(name).apply(descriptorBuilder)
        registerProperty(converter, descriptor, state)
        ReadOnlyProperty { _: DeviceConstructor, _ ->
            state
        }
    }

/**
 * Register an external state as a property
 */
public fun <T : Any> DeviceConstructor.property(
    metaConverter: MetaConverter<T>,
    reader: suspend () -> T,
    readInterval: Duration,
    initialState: T,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, ValueState<T>>> = property(
    converter = metaConverter,
    state = ValueState.external(this, readInterval, initialState, clock, reader),
    descriptorBuilder = descriptorBuilder,
    nameOverride = nameOverride,
)

/**
 * Create and register a mutable external state as a property
 */
public fun <T : Any> DeviceConstructor.mutableProperty(
    metaConverter: MetaConverter<T>,
    reader: suspend () -> T,
    writer: suspend (T) -> Unit,
    readInterval: Duration,
    initialState: T,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, MutableValueState<T>>> = property(
    converter = metaConverter,
    state = ValueState.external(this, readInterval, initialState, clock, reader, writer),
    descriptorBuilder = descriptorBuilder,
    nameOverride = nameOverride,
)

/**
 * Create and register a virtual mutable property
 */
public fun <T> DeviceConstructor.virtualProperty(
    metaConverter: MetaConverter<T>,
    initialState: T,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, MutableValueState<T>>> = property(
    converter = metaConverter,
    state = MutableValueState(initialState, clock),
    descriptorBuilder = descriptorBuilder,
    nameOverride = nameOverride,
)

public fun <T, S : ValueState<T>> DeviceConstructor.registerAsProperty(
    spec: DevicePropertySpec<T>,
    state: S,
): S {
    registerProperty(spec.converter, spec.descriptor, state)
    return state
}
