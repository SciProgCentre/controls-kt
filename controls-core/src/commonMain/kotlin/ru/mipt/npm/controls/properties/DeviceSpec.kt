package ru.mipt.npm.controls.properties

import kotlinx.coroutines.withContext
import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

@OptIn(InternalDeviceAPI::class)
public abstract class DeviceSpec<D : DeviceBySpec<D>>(
    private val buildDevice: () -> D
) : Factory<D> {
    private val _properties = HashMap<String, DevicePropertySpec<D, *>>()
    public val properties: Map<String, DevicePropertySpec<D, *>> get() = _properties

    private val _actions = HashMap<String, DeviceActionSpec<D, *, *>>()
    public val actions: Map<String, DeviceActionSpec<D, *, *>> get() = _actions

    public fun <T : Any> registerProperty(deviceProperty: DevicePropertySpec<D, T>): DevicePropertySpec<D, T> {
        _properties[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    public fun <T : Any> registerProperty(
        converter: MetaConverter<T>,
        readOnlyProperty: KProperty1<D, T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {}
    ): DevicePropertySpec<D, T> {
        val deviceProperty = object : DevicePropertySpec<D, T> {
            override val name: String = readOnlyProperty.name
            override val descriptor: PropertyDescriptor = PropertyDescriptor(this.name).apply(descriptorBuilder)
            override val converter: MetaConverter<T> = converter
            override suspend fun read(device: D): T =
                withContext(device.coroutineContext) { readOnlyProperty.get(device) }
        }
        return registerProperty(deviceProperty)
    }

    public fun <T : Any> registerProperty(
        converter: MetaConverter<T>,
        readWriteProperty: KMutableProperty1<D, T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {}
    ): WritableDevicePropertySpec<D, T> {
        val deviceProperty = object : WritableDevicePropertySpec<D, T> {
            override val name: String = readWriteProperty.name
            override val descriptor: PropertyDescriptor = PropertyDescriptor(this.name).apply(descriptorBuilder)
            override val converter: MetaConverter<T> = converter
            override suspend fun read(device: D): T = withContext(device.coroutineContext) {
                readWriteProperty.get(device)
            }

            override suspend fun write(device: D, value: T) = withContext(device.coroutineContext) {
                readWriteProperty.set(device, value)
            }
        }
        registerProperty(deviceProperty)
        return deviceProperty
    }

    public fun <T : Any> property(
        converter: MetaConverter<T>,
        name: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        read: suspend D.() -> T
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property ->
            val propertyName = name ?: property.name
            val deviceProperty = object : DevicePropertySpec<D, T> {
                override val name: String = propertyName
                override val descriptor: PropertyDescriptor = PropertyDescriptor(this.name).apply(descriptorBuilder)
                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T = withContext(device.coroutineContext) { device.read() }
            }
            _properties[propertyName] = deviceProperty
            ReadOnlyProperty<DeviceSpec<D>, DevicePropertySpec<D, T>> { _, _ ->
                deviceProperty
            }
        }

    public fun <T : Any> property(
        converter: MetaConverter<T>,
        name: String? = null,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        read: suspend D.() -> T,
        write: suspend D.(T) -> Unit
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property: KProperty<*> ->
            val propertyName = name ?: property.name
            val deviceProperty = object : WritableDevicePropertySpec<D, T> {
                override val name: String = propertyName
                override val descriptor: PropertyDescriptor = PropertyDescriptor(this.name).apply(descriptorBuilder)
                override val converter: MetaConverter<T> = converter

                override suspend fun read(device: D): T = withContext(device.coroutineContext) { device.read() }

                override suspend fun write(device: D, value: T) = withContext(device.coroutineContext) {
                    device.write(value)
                }
            }
            _properties[propertyName] = deviceProperty
            ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, T>> { _, _ ->
                deviceProperty
            }
        }


    public fun <I : Any, O : Any> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
        _actions[deviceAction.name] = deviceAction
        return deviceAction
    }

    public fun <I : Any, O : Any> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        name: String? = null,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        execute: suspend D.(I?) -> O?
    ): PropertyDelegateProvider<DeviceSpec<D>, ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _: DeviceSpec<D>, property ->
            val actionName = name ?: property.name
            val deviceAction = object : DeviceActionSpec<D, I, O> {
                override val name: String = actionName
                override val descriptor: ActionDescriptor = ActionDescriptor(actionName).apply(descriptorBuilder)

                override val inputConverter: MetaConverter<I> = inputConverter
                override val outputConverter: MetaConverter<O> = outputConverter

                override suspend fun execute(device: D, input: I?): O? = withContext(device.coroutineContext) {
                    device.execute(input)
                }
            }
            _actions[actionName] = deviceAction
            ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, I, O>> { _, _ ->
                deviceAction
            }
        }

    /**
     * The function is executed right after device initialization is finished
     */
    public open fun D.onStartup() {}

    /**
     * The function is executed before device is shut down
     */
    public open fun D.onShutdown() {}


    override fun invoke(meta: Meta, context: Context): D = buildDevice().apply {
        this.context = context
        this.meta = meta
        onStartup()
    }
}
