package space.kscience.controls.spec

import kotlinx.coroutines.CompletableDeferred
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.api.metaDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * A device specification that provides a builder for device instance that adheres to this specification
 *
 * @param S the type of device state
 */
public abstract class DeviceFactory<S : Any> : DeviceSpec, Factory<Device> {

    public abstract suspend fun DeviceBase.createState(): S

    public open suspend fun DeviceBase.destroyState(state: S): Unit {

    }

    //initializing the metadata property for everyone
    private val _properties = hashMapOf<String, DevicePropertySpec<*>>(
        DeviceMetaPropertySpec.name to DeviceMetaPropertySpec
    )
    override val properties: Map<String, DevicePropertySpec<*>> get() = _properties


    private val readers: MutableMap<DevicePropertySpec<*>, suspend context(DeviceBase) S.() -> Any?> = mutableMapOf()

    private val writers: MutableMap<DevicePropertySpec<*>, suspend context(DeviceBase) S.(Any?) -> Unit> = mutableMapOf()

    private val logical: MutableSet<DevicePropertySpecWithDefault<*>> = mutableSetOf()

    private val _actions = HashMap<String, DeviceActionSpec<*, *>>()
    override val actions: Map<String, DeviceActionSpec<*, *>> get() = _actions


    public fun <T, P : DevicePropertySpec<T>> registerProperty(
        deviceProperty: P,
        reader: suspend context(DeviceBase) S.() -> T
    ): P {
        check(deviceProperty.isReadable) { "Property ${deviceProperty.name} is not readable" }
        check(!deviceProperty.isMutable) { "Property ${deviceProperty.name} is mutable" }
        _properties[deviceProperty.name] = deviceProperty
        readers[deviceProperty] = reader
        return deviceProperty
    }

    public fun <T, P : DevicePropertySpec<T>> registerMutableProperty(
        deviceProperty: P,
        reader: suspend context(DeviceBase) S.() -> T,
        writer: suspend context(DeviceBase) S.(T) -> Unit
    ): P {
        check(deviceProperty.isReadable) { "Property ${deviceProperty.name} is not readable" }
        check(deviceProperty.isMutable) { "Property ${deviceProperty.name} is not mutable" }
        _properties[deviceProperty.name] = deviceProperty

        readers[deviceProperty] = reader
        @Suppress("UNCHECKED_CAST")
        writers[deviceProperty] = writer as suspend context(DeviceBase) S.(Any?) -> Unit
        return deviceProperty
    }

    public fun <T> registerLogicalProperty(
        spec: DevicePropertySpecWithDefault<T>,
    ): DevicePropertySpec<T> {
        check(spec.isReadable) { "Property ${spec.name} is not readable" }
        check(spec.isMutable) { "Property ${spec.name} is not mutable" }
        _properties[spec.name] = spec

        logical.add(spec)

        return spec
    }

    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        name: String? = null,
        reader: suspend context(DeviceBase) S.() -> T
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

            registerProperty(deviceProperty, reader)
            ReadOnlyProperty<DeviceSpec, DevicePropertySpec<T>> { _, _ ->
                deviceProperty
            }
        }

    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        name: String? = null,
        reader: suspend context(DeviceBase) S.() -> T,
        writer: suspend context(DeviceBase) S.(T) -> Unit
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

            registerMutableProperty(deviceProperty, reader, writer)
            ReadOnlyProperty<DeviceSpec, DevicePropertySpec<T>> { _, _ ->
                deviceProperty
            }
        }

    public fun <T> logicalProperty(
        converter: MetaConverter<T>,
        defaultValue: T,
        descriptorBuilder: PropertyDescriptor.() -> Unit = {},
        name: String? = null,
    ): PropertyDelegateProvider<DeviceSpec, ReadOnlyProperty<DeviceSpec, DevicePropertySpecWithDefault<T>>> =
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

            val deviceProperty = DevicePropertySpecWithDefault(converter, descriptor, defaultValue)

            registerLogicalProperty(deviceProperty)
            ReadOnlyProperty<DeviceSpec, DevicePropertySpecWithDefault<T>> { _, _ ->
                deviceProperty
            }
        }

    private val actionFunctions: MutableMap<DeviceActionSpec<*, *>, suspend S.(Any?) -> Any?> = hashMapOf()

    public fun <I, O> registerAction(
        spec: DeviceActionSpec<I, O>,
        execute: suspend S.(I) -> O
    ): DeviceActionSpec<I, O> {
        _actions[spec.name] = spec
        @Suppress("UNCHECKED_CAST")
        actionFunctions[spec] = execute as suspend S.(Any?) -> Any?

        return spec
    }

    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        name: String? = null,
        execute: suspend S.(I) -> O
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

            registerAction(deviceAction, execute)

            ReadOnlyProperty<DeviceSpec, DeviceActionSpec<I, O>> { _, _ ->
                deviceAction
            }
        }


    /**
     * Create an instance of a device that adheres to this specification.
     *
     * The instance incapsulates the state [S], which is created and destroyed on device start and stop.
     */
    override fun build(context: Context, meta: Meta): Device = Device(context, meta) {
        val state = CompletableDeferred<S>()

        readers.forEach { (property, reader) ->
            @Suppress("UNCHECKED_CAST")
            this.reader(property as DevicePropertySpec<Any?>) {
                state.await().reader()
            }
        }

        writers.forEach { (property, writer) ->
            this.writer(property) {
                state.await().writer(it)
            }
        }

        logical.forEach { spec ->
            this.logical(spec)
        }

        actionFunctions.forEach { (spec, execute) ->
            @Suppress("UNCHECKED_CAST")
            this.action(spec as DeviceActionSpec<Any?, Any?>) {
                state.await().execute(it)
            }
        }

        onStart {
            state.complete(createState())
        }


        onStop {
            if (state.isCompleted) destroyState(state.await())
            state.cancel()
        }
    }


}


/**
 * A read-only device property that delegates reading to a device [KProperty1]
 */
public fun <T, S : Any> DeviceFactory<S>.property(
    converter: MetaConverter<T>,
    readOnlyProperty: KProperty1<S, T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<T>>> =
    property(
        converter,
        descriptorBuilder,
        name = readOnlyProperty.name,
        reader = { readOnlyProperty.get(this) }
    )

/**
 * Mutable property that delegates reading and writing to a device [KMutableProperty1]
 */
public fun <T, S : Any> DeviceFactory<S>.mutableProperty(
    converter: MetaConverter<T>,
    readWriteProperty: KMutableProperty1<S, T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<T>>> =
    mutableProperty(
        converter,
        descriptorBuilder,
        readWriteProperty.name,
        reader = { readWriteProperty.get(this) },
        writer = { readWriteProperty.set(this, it) }
    )

//read only delegates


public fun <S : Any> DeviceFactory<S>.booleanProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Boolean
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Boolean>>> =
    property(
        MetaConverter.boolean,
        {
            metaDescriptor {
                valueType(ValueType.BOOLEAN)
            }
            descriptorBuilder()
        },
        name,
        read
    )

private inline fun numberDescriptor(
    crossinline descriptorBuilder: PropertyDescriptor.() -> Unit = {}
): PropertyDescriptor.() -> Unit = {
    metaDescriptor {
        valueType(ValueType.NUMBER)
    }
    descriptorBuilder()
}

public fun <S : Any> DeviceFactory<S>.numberProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Number
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Number>>> =
    property(
        MetaConverter.number,
        numberDescriptor(descriptorBuilder),
        name,
        read
    )

public fun <S : Any> DeviceFactory<S>.doubleProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Double
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Double>>> =
    property(
        MetaConverter.double,
        numberDescriptor(descriptorBuilder),
        name,
        read
    )

public fun <S : Any> DeviceFactory<S>.stringProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> String
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<String>>> =
    property(
        MetaConverter.string,
        {
            metaDescriptor {
                valueType(ValueType.STRING)
            }
            descriptorBuilder()
        },
        name,
        read
    )

public fun <S : Any> DeviceFactory<S>.metaProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Meta
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Meta>>> =
    property(
        MetaConverter.meta,
        {
            metaDescriptor {
                valueType(ValueType.STRING)
            }
            descriptorBuilder()
        },
        name,
        read
    )

//read-write delegates


public fun <S : Any> DeviceFactory<S>.mutableBooleanProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Boolean,
    write: suspend context(DeviceBase) S.(value: Boolean) -> Unit
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Boolean>>> =
    mutableProperty(
        MetaConverter.boolean,
        {
            metaDescriptor {
                valueType(ValueType.BOOLEAN)
            }
            descriptorBuilder()
        },
        name,
        read,
        write
    )


public fun <S : Any> DeviceFactory<S>.mutableNumberProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Number,
    write: suspend context(DeviceBase) S.(value: Number) -> Unit
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Number>>> =
    mutableProperty(MetaConverter.number, numberDescriptor(descriptorBuilder), name, read, write)

public fun <S : Any> DeviceFactory<S>.mutableDoubleProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Double,
    write: suspend context(DeviceBase) S.(value: Double) -> Unit
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Double>>> =
    mutableProperty(MetaConverter.double, numberDescriptor(descriptorBuilder), name, read, write)

public fun <S : Any> DeviceFactory<S>.mutableStringProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> String,
    write: suspend context(DeviceBase) S.(value: String) -> Unit
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<String>>> =
    mutableProperty(MetaConverter.string, descriptorBuilder, name, read, write)

public fun <S : Any> DeviceFactory<S>.mutableMetaProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Meta,
    write: suspend context(DeviceBase) S.(value: Meta) -> Unit
): PropertyDelegateProvider<DeviceFactory<S>, ReadOnlyProperty<DeviceFactory<S>, DevicePropertySpec<Meta>>> =
    mutableProperty(MetaConverter.meta, descriptorBuilder, name, read, write)


/**
 * A device specification base that uses [MutableMeta] as device state.
 *
 */
public abstract class MetaDeviceFactory : DeviceFactory<MutableMeta>() {
    override suspend fun DeviceBase.createState(): MutableMeta = MutableMeta()
}

/**
 *  A device specification base that uses [Unit] as device state.
 */
public abstract class StatelessDeviceFactory : DeviceFactory<Unit>() {
    override suspend fun DeviceBase.createState(): Unit = Unit
}