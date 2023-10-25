package space.kscience.controls.spec

import kotlinx.coroutines.withContext
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

public object UnitMetaConverter : MetaConverter<Unit> {
    override fun metaToObject(meta: Meta): Unit = Unit

    override fun objectToMeta(obj: Unit): Meta = Meta.EMPTY
}

public val MetaConverter.Companion.unit: MetaConverter<Unit> get() = UnitMetaConverter

@OptIn(InternalDeviceAPI::class)
public abstract class DeviceSpec<D : Device> {
    //initializing the metadata property for everyone
    private val _properties = hashMapOf<String, DevicePropertySpec<D, *>>(
        DeviceMetaPropertySpec.name to DeviceMetaPropertySpec
    )
    public val properties: Map<String, DevicePropertySpec<D, *>> get() = _properties

    private val _actions = HashMap<String, DeviceActionSpec<D, *, *>>()
    public val actions: Map<String, DeviceActionSpec<D, *, *>> get() = _actions


    public open suspend fun D.onOpen() {
    }

    public open fun D.onClose() {
    }


    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P {
        _properties[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    public fun <T> property(
        converter: MetaConverter<T>,
        readOnlyProperty: KProperty1<D, T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<Any?, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val deviceProperty = object : DevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = PropertyDescriptor(property.name).apply {
                    //TODO add type from converter
                    mutable = true
                }.apply(descriptorBuilder)

                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T = withContext(device.coroutineContext) {
                    readOnlyProperty.get(device)
                }
            }
            registerProperty(deviceProperty)
            ReadOnlyProperty { _, _ ->
                deviceProperty
            }
        }

    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        readWriteProperty: KMutableProperty1<D, T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<Any?, WritableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val deviceProperty = object : WritableDevicePropertySpec<D, T> {

                override val descriptor: PropertyDescriptor = PropertyDescriptor(property.name).apply {
                    //TODO add the type from converter
                    mutable = true
                }.apply(descriptorBuilder)

                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T = withContext(device.coroutineContext) {
                    readWriteProperty.get(device)
                }

                override suspend fun write(device: D, value: T): Unit = withContext(device.coroutineContext) {
                    readWriteProperty.set(device, value)
                }
            }
            registerProperty(deviceProperty)
            ReadOnlyProperty { _, _ ->
                deviceProperty
            }
        }

    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        name: String? = null,
        read: suspend D.() -> T?,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property ->
            val propertyName = name ?: property.name
            val deviceProperty = object : DevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = PropertyDescriptor(propertyName).apply(descriptorBuilder)
                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T? = withContext(device.coroutineContext) { device.read() }
            }
            registerProperty(deviceProperty)
            ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, T>> { _, _ ->
                deviceProperty
            }
        }

    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        name: String? = null,
        read: suspend D.() -> T?,
        write: suspend D.(T) -> Unit,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property: KProperty<*> ->
            val propertyName = name ?: property.name
            val deviceProperty = object : WritableDevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = PropertyDescriptor(propertyName, mutable = true)
                    .apply(descriptorBuilder)
                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T? = withContext(device.coroutineContext) { device.read() }

                override suspend fun write(device: D, value: T): Unit = withContext(device.coroutineContext) {
                    device.write(value)
                }
            }
            _properties[propertyName] = deviceProperty
            ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, T>> { _, _ ->
                deviceProperty
            }
        }


    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
        _actions[deviceAction.name] = deviceAction
        return deviceAction
    }

    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(I) -> O,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property ->
            val actionName = name ?: property.name
            val deviceAction = object : DeviceActionSpec<D, I, O> {
                override val descriptor: ActionDescriptor = ActionDescriptor(actionName).apply(descriptorBuilder)

                override val inputConverter: MetaConverter<I> = inputConverter
                override val outputConverter: MetaConverter<O> = outputConverter

                override suspend fun execute(device: D, input: I): O = withContext(device.coroutineContext) {
                    device.execute(input)
                }
            }
            _actions[actionName] = deviceAction
            ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, I, O>> { _, _ ->
                deviceAction
            }
        }

    /**
     * An action that takes [Meta] and returns [Meta]. No conversions are done
     */
    public fun metaAction(
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(Meta) -> Meta,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, Meta, Meta>>> =
        action(
            MetaConverter.Companion.meta,
            MetaConverter.Companion.meta,
            descriptorBuilder,
            name
        ) {
            execute(it)
        }

    /**
     * An action that takes no parameters and returns no values
     */
    public fun unitAction(
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        name: String? = null,
        execute: suspend D.() -> Unit,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, Unit, Unit>>> =
        action(
            MetaConverter.Companion.unit,
            MetaConverter.Companion.unit,
            descriptorBuilder,
            name
        ) {
            execute()
        }
}


/**
 * Register a mutable logical property for a device
 */
@OptIn(InternalDeviceAPI::class)
public fun <T, D : DeviceBase<D>> DeviceSpec<D>.logicalProperty(
    converter: MetaConverter<T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<Any?, WritableDevicePropertySpec<D, T>>> =
    PropertyDelegateProvider { _, property ->
        val deviceProperty = object : WritableDevicePropertySpec<D, T> {
            val propertyName = name ?: property.name
            override val descriptor: PropertyDescriptor = PropertyDescriptor(propertyName).apply {
                //TODO add type from converter
                mutable = true
            }.apply(descriptorBuilder)

            override val converter: MetaConverter<T> = converter

            override suspend fun read(device: D): T? = device.getProperty(propertyName)?.let(converter::metaToObject)

            override suspend fun write(device: D, value: T): Unit =
                device.writeProperty(propertyName, converter.objectToMeta(value))
        }
        registerProperty(deviceProperty)
        ReadOnlyProperty { _, _ ->
            deviceProperty
        }
    }