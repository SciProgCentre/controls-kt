package space.kscience.controls.constructor

import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * A base for strongly typed device constructor blocks. Has additional delegates for type-safe devices
 */
public abstract class DeviceConstructor(
    deviceManager: DeviceManager,
    meta: Meta,
) : DeviceGroup(deviceManager, meta) {

    /**
     * Register a device, provided by a given [factory] and
     */
    public fun <D : Device> device(
        factory: Factory<D>,
        meta: Meta? = null,
        nameOverride: Name? = null,
        metaLocation: Name? = null,
    ): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, D>> =
        PropertyDelegateProvider { _: DeviceConstructor, property: KProperty<*> ->
            val name = nameOverride ?: property.name.asName()
            val device = registerDevice(name, factory, meta, metaLocation ?: name)
            ReadOnlyProperty { _: DeviceConstructor, _ ->
                device
            }
        }

    public fun <D : Device> device(
        device: D,
        nameOverride: Name? = null,
    ): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, D>> =
        PropertyDelegateProvider { _: DeviceConstructor, property: KProperty<*> ->
            val name = nameOverride ?: property.name.asName()
            registerDevice(name, device)
            ReadOnlyProperty { _: DeviceConstructor, _ ->
                device
            }
        }


    /**
     * Register a property and provide a direct reader for it
     */
    public fun <T : Any> property(
        state: DeviceState<T>,
        nameOverride: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit,
    ): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, T>> =
        PropertyDelegateProvider { _: DeviceConstructor, property ->
            val name = nameOverride ?: property.name
            val descriptor = PropertyDescriptor(name).apply(descriptorBuilder)
            registerProperty(descriptor, state)
            ReadOnlyProperty { _: DeviceConstructor, _ ->
                state.value
            }
        }

    /**
     * Register external state as a property
     */
    public fun <T : Any> property(
        metaConverter: MetaConverter<T>,
        reader: suspend () -> T,
        readInterval: Duration,
        initialState: T,
        nameOverride: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit,
    ): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, T>> = property(
        DeviceState.external(this, metaConverter, readInterval, initialState, reader),
        nameOverride, descriptorBuilder
    )


    /**
     * Register a mutable property and provide a direct reader for it
     */
    public fun <T : Any> mutableProperty(
        state: MutableDeviceState<T>,
        nameOverride: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit,
    ): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, T>> =
        PropertyDelegateProvider { _: DeviceConstructor, property ->
            val name = nameOverride ?: property.name
            val descriptor = PropertyDescriptor(name).apply(descriptorBuilder)
            registerProperty(descriptor, state)
            object : ReadWriteProperty<DeviceConstructor, T> {
                override fun getValue(thisRef: DeviceConstructor, property: KProperty<*>): T = state.value

                override fun setValue(thisRef: DeviceConstructor, property: KProperty<*>, value: T) {
                    state.value = value
                }

            }
        }

    /**
     * Register external state as a property
     */
    public fun <T : Any> mutableProperty(
        metaConverter: MetaConverter<T>,
        reader: suspend () -> T,
        writer: suspend (T) -> Unit,
        readInterval: Duration,
        initialState: T,
        nameOverride: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit,
    ): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, T>> = mutableProperty(
        DeviceState.external(this, metaConverter, readInterval, initialState, reader, writer),
        nameOverride,
        descriptorBuilder
    )
}