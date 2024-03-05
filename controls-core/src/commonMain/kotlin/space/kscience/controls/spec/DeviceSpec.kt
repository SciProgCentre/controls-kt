package space.kscience.controls.spec

import kotlinx.coroutines.withContext
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

public object UnitMetaConverter : MetaConverter<Unit> {

    override fun readOrNull(source: Meta): Unit = Unit

    override fun convert(obj: Unit): Meta = Meta.EMPTY
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
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property ->
            val propertyName = name ?: property.name
            val deviceProperty = object : DevicePropertySpec<D, T> {

                override val descriptor: PropertyDescriptor = PropertyDescriptor(propertyName).apply {
                    fromSpec(property)
                    descriptorBuilder()
                }

                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }
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
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit,
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property: KProperty<*> ->
            val propertyName = name ?: property.name
            val deviceProperty = object : MutableDevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = PropertyDescriptor(
                    propertyName,
                    mutable = true
                ).apply {
                    fromSpec(property)
                    descriptorBuilder()
                }
                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }

                override suspend fun write(device: D, value: T): Unit = withContext(device.coroutineContext) {
                    device.write(propertyName, value)
                }
            }
            registerProperty(deviceProperty)
            ReadOnlyProperty<DeviceSpec<D>, MutableDevicePropertySpec<D, T>> { _, _ ->
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
        PropertyDelegateProvider { _: DeviceSpec<D>, property: KProperty<*> ->
            val actionName = name ?: property.name
            val deviceAction = object : DeviceActionSpec<D, I, O> {
                override val descriptor: ActionDescriptor = ActionDescriptor(actionName).apply {
                    fromSpec(property)
                    descriptorBuilder()
                }

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

}

/**
 * An action that takes no parameters and returns no values
 */
public fun <D : Device> DeviceSpec<D>.unitAction(
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

/**
 * An action that takes [Meta] and returns [Meta]. No conversions are done
 */
public fun <D : Device> DeviceSpec<D>.metaAction(
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

