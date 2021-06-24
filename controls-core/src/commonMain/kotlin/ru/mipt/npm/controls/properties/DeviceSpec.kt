package ru.mipt.npm.controls.properties

import kotlinx.coroutines.Deferred
import ru.mipt.npm.controls.api.ActionDescriptor
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

public abstract class DeviceSpec<D : DeviceBySpec<D>>(
    private val buildDevice: () -> D
) : Factory<D> {
    private val deviceProperties = HashMap<String, DevicePropertySpec<D, *>>()
    private val deviceActions = HashMap<String, DeviceActionSpec<D, *, *>>()

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

                override suspend fun read(device: D): T = device.read()
            }
            deviceProperties[propertyName] = deviceProperty
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

                override suspend fun read(device: D): T = device.read()

                override suspend fun write(device: D, value: T) {
                    device.write(value)
                }
            }
            deviceProperties[propertyName] = deviceProperty
            ReadOnlyProperty<DeviceSpec<D>, WritableDevicePropertySpec<D, T>> { _, _ ->
                deviceProperty
            }
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

                override suspend fun execute(device: D, input: I?): O? {
                    return device.execute(input)
                }
            }
            deviceActions[actionName] = deviceAction
            ReadOnlyProperty<DeviceSpec<D>, DeviceActionSpec<D, I, O>> { _, _ ->
                deviceAction
            }
        }


    override fun invoke(meta: Meta, context: Context): D = buildDevice().apply {
        this.context = context
        this.meta = meta
        this.properties = deviceProperties
        this.actions = deviceActions
    }
}