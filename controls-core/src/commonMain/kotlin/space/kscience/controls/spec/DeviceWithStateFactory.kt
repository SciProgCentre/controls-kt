package space.kscience.controls.spec

import kotlinx.coroutines.CompletableDeferred
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.node
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * A builder for a device with a given state of type [S]
 */
public open class DeviceWithStateBuilder<S : Any> : DeviceSpec {

    //initializing the metadata property for everyone
    private val _properties = hashMapOf<String, DevicePropertySpec<*>>()
    override val properties: Map<String, DevicePropertySpec<*>> get() = _properties


    protected val readers: MutableMap<DevicePropertySpec<*>, suspend context(DeviceBase) S.() -> Any?> = mutableMapOf()

    protected val writers: MutableMap<DevicePropertySpec<*>, suspend context(DeviceBase) S.(Any?) -> Unit> =
        mutableMapOf()

    protected val logical: MutableSet<DevicePropertySpecWithDefault<*>> = mutableSetOf()

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

    protected val actionFunctions: MutableMap<DeviceActionSpec<*, *>, suspend context(DeviceBase) S.(Any?) -> Any?> =
        hashMapOf()

    public fun <I, O> registerAction(
        spec: DeviceActionSpec<I, O>,
        execute: suspend context(DeviceBase) S.(I) -> O
    ): DeviceActionSpec<I, O> {
        _actions[spec.name] = spec
        @Suppress("UNCHECKED_CAST")
        actionFunctions[spec] = execute as suspend context(DeviceBase) S.(Any?) -> Any?

        return spec
    }

    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptor.() -> Unit = {},
        name: String? = null,
        execute: suspend context(DeviceBase) S.(I) -> O
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
     * Build a device with the given context and meta, using the provided state creation and destruction functions.
     */
    public fun build(
        context: Context,
        meta: Meta,
        destroyState: suspend context(DeviceBase) (S) -> Unit = {},
        createState: suspend context(DeviceBase) () -> S
    ): Device = Device(context, meta) {
        val state = CompletableDeferred<S>()

        reader(DevicePropertySpec.deviceMeta) { meta }

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
 * A device specification that provides a builder for a device instance that adheres to this specification
 *
 * @param S the type of device state
 */
public abstract class DeviceWithStateFactory<S : Any> : DeviceWithStateBuilder<S>(), DeviceFactory {

    context(device: DeviceBase)
    public abstract suspend fun createState(): S

    context(device: DeviceBase)
    public open suspend fun destroyState(state: S): Unit {

    }

    override val descriptor: MetaDescriptor = MetaDescriptor {
        node(DevicePropertySpec.deviceMeta.name, MetaDescriptor())
    }

    /**
     * Create an instance of a device that adheres to this specification.
     *
     * The instance incapsulates the state [S], which is created and destroyed on device start and stop.
     */
    override fun buildDevice(context: Context, meta: Meta): Device = build(
        context = context,
        meta = meta,
        destroyState = { destroyState(it) },
        createState = { createState() }
    )


}


/**
 * A read-only device property that delegates reading to a device [KProperty1]
 */
public fun <T, S : Any> DeviceWithStateBuilder<S>.property(
    converter: MetaConverter<T>,
    readOnlyProperty: KProperty1<S, T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<T>>> =
    property(
        converter,
        descriptorBuilder,
        name = readOnlyProperty.name,
        reader = { readOnlyProperty.get(this) }
    )

/**
 * Mutable property that delegates reading and writing to a device [KMutableProperty1]
 */
public fun <T, S : Any> DeviceWithStateBuilder<S>.mutableProperty(
    converter: MetaConverter<T>,
    readWriteProperty: KMutableProperty1<S, T>,
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<T>>> =
    mutableProperty(
        converter,
        descriptorBuilder,
        readWriteProperty.name,
        reader = { readWriteProperty.get(this) },
        writer = { readWriteProperty.set(this, it) }
    )

//read only delegates


public fun <S : Any> DeviceWithStateBuilder<S>.booleanProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Boolean
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Boolean>>> =
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

public fun <S : Any> DeviceWithStateBuilder<S>.numberProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Number
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Number>>> =
    property(
        MetaConverter.number,
        numberDescriptor(descriptorBuilder),
        name,
        read
    )

public fun <S : Any> DeviceWithStateBuilder<S>.doubleProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Double
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Double>>> =
    property(
        MetaConverter.double,
        numberDescriptor(descriptorBuilder),
        name,
        read
    )

public fun <S : Any> DeviceWithStateBuilder<S>.stringProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> String
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<String>>> =
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

public fun <S : Any> DeviceWithStateBuilder<S>.metaProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Meta
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Meta>>> =
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


public fun <S : Any> DeviceWithStateBuilder<S>.mutableBooleanProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Boolean,
    write: suspend context(DeviceBase) S.(value: Boolean) -> Unit
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Boolean>>> =
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


public fun <S : Any> DeviceWithStateBuilder<S>.mutableNumberProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Number,
    write: suspend context(DeviceBase) S.(value: Number) -> Unit
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Number>>> =
    mutableProperty(MetaConverter.number, numberDescriptor(descriptorBuilder), name, read, write)

public fun <S : Any> DeviceWithStateBuilder<S>.mutableDoubleProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Double,
    write: suspend context(DeviceBase) S.(value: Double) -> Unit
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Double>>> =
    mutableProperty(MetaConverter.double, numberDescriptor(descriptorBuilder), name, read, write)

public fun <S : Any> DeviceWithStateBuilder<S>.mutableStringProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> String,
    write: suspend context(DeviceBase) S.(value: String) -> Unit
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<String>>> =
    mutableProperty(MetaConverter.string, descriptorBuilder, name, read, write)

public fun <S : Any> DeviceWithStateBuilder<S>.mutableMetaProperty(
    descriptorBuilder: PropertyDescriptor.() -> Unit = {},
    name: String? = null,
    read: suspend context(DeviceBase) S.() -> Meta,
    write: suspend context(DeviceBase) S.(value: Meta) -> Unit
): PropertyDelegateProvider<DeviceWithStateBuilder<S>, ReadOnlyProperty<DeviceWithStateBuilder<S>, DevicePropertySpec<Meta>>> =
    mutableProperty(MetaConverter.meta, descriptorBuilder, name, read, write)


/**
 * A device specification base that uses [MutableMeta] as device state.
 *
 */
public abstract class MetaDeviceFactory : DeviceWithStateFactory<MutableMeta>() {
    context(device: DeviceBase)
    override suspend fun createState(): MutableMeta = MutableMeta {
        "@device" put device.meta
    }
}

/**
 *  A device specification base that uses [Unit] as device state.
 */
public abstract class StatelessDeviceFactory : DeviceWithStateFactory<Unit>() {
    context(device: DeviceBase)
    override suspend fun createState(): Unit = Unit
}