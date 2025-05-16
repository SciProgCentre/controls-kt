package space.kscience.controls.spec.api

import kotlinx.coroutines.withContext
import space.kscience.controls.api.*
import space.kscience.controls.constructor.*
import space.kscience.controls.spec.ConfigurableCompositeControlComponent
import space.kscience.controls.spec.DeviceActionSpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.InternalDeviceAPI
import space.kscience.controls.spec.MutableDevicePropertySpec
import space.kscience.controls.api.DeviceConfigurationException
import space.kscience.controls.spec.config.DeviceLifecycleConfigBuilder
import space.kscience.controls.spec.name
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Interface defining a composite device specification with properties, actions, and child specs.
 *
 * @param D The device type this spec applies to.
 */
public interface CompositeDeviceSpec<D : ConfigurableCompositeControlComponent<D>> {
    /** Map of property specifications. */
    public val properties: Map<String, DevicePropertySpec<D, *>>
    /** Map of action specifications. */
    public val actions: Map<String, DeviceActionSpec<D, *, *>>
    /** Map of child component configurations. */
    public val childSpecs: Map<String, ChildComponentConfig<*>>

    /**
     * Called when the device is opening (starting).
     *
     * @receiver The device instance.
     */
    public suspend fun D.onOpen()

    /**
     * Called when the device is closing (stopping).
     *
     * @receiver The device instance.
     */
    public suspend fun D.onClose()

    /**
     * Validates the [device]'s state or properties. Throws an exception if validation fails.
     *
     * @param device The device instance to validate.
     */
    public fun validate(device: D)

    /**
     * Registers a [deviceProperty].
     *
     * @param deviceProperty The property specification to register.
     * @return The registered property specification.
     */
    public fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P

    /**
     * Registers a [deviceAction].
     *
     * @param deviceAction The action specification to register.
     * @return The registered action specification.
     */
    public fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O>

    /**
     * Declares a read-only property with the given [converter].
     *
     * @param converter The meta converter for the property.
     * @param descriptorBuilder Optional builder for the property descriptor.
     * @param name An optional name override.
     * @param read A suspend function to read the property value.
     * @return A property delegate provider for the declared property.
     */
    public fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>>

    /**
     * Declares a mutable property with the given [converter].
     *
     * @param converter The meta converter for the property.
     * @param descriptorBuilder Optional builder for the property descriptor.
     * @param name An optional name override.
     * @param read A suspend function to read the property value.
     * @param write A suspend function to write the property value.
     * @return A property delegate provider for the declared mutable property.
     */
    public fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>>

    /**
     * Declares an action with the specified input and output converters and execution block.
     *
     * @param inputConverter The meta converter for the action input.
     * @param outputConverter The meta converter for the action output.
     * @param descriptorBuilder Optional builder for the action descriptor.
     * @param name An optional name override.
     * @param execute The suspend function to execute the action.
     * @return A property delegate provider for the declared action.
     */
    public fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit = {},
        name: String? = null,
        execute: suspend D.(input: I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>>
}

/**
 * Default implementation of [CompositeDeviceSpec].
 *
 * @param D The device type.
 * @property registry Optional [ComponentRegistry] for looking up child specifications.
 */
@OptIn(InternalDeviceAPI::class)
public open class CompositeControlComponentSpec<D : ConfigurableCompositeControlComponent<D>>(
    public val registry: ComponentRegistry? = null
) : CompositeDeviceSpec<D> {
    private val propertyMap = hashMapOf<String, DevicePropertySpec<D, *>>()
    private val actionMap = hashMapOf<String, DeviceActionSpec<D, *, *>>()
    private val childConfigMap = mutableMapOf<String, ChildComponentConfig<*>>()
    private val stateMap = mutableMapOf<String, DeviceState<*>>()

    override val properties: Map<String, DevicePropertySpec<D, *>> get() = propertyMap
    override val actions: Map<String, DeviceActionSpec<D, *, *>> get() = actionMap
    override val childSpecs: Map<String, ChildComponentConfig<*>> get() = childConfigMap

    /**
     * Map of state objects defined in this spec.
     */
    public val states: Map<String, DeviceState<*>> get() = stateMap

    override suspend fun D.onOpen() {
        // Default implementation is no-op.
    }

    override suspend fun D.onClose() {
        // Default implementation is no-op.
    }

    override fun validate(device: D) {
        validateSpec(device)
    }

    override fun <T, P : DevicePropertySpec<D, T>> registerProperty(deviceProperty: P): P {
        if (propertyMap.containsKey(deviceProperty.name)) {
            throw DeviceConfigurationException("Property ${deviceProperty.name} is already registered.")
        }
        propertyMap[deviceProperty.name] = deviceProperty
        return deviceProperty
    }

    override fun <I, O> registerAction(deviceAction: DeviceActionSpec<D, I, O>): DeviceActionSpec<D, I, O> {
        if (actionMap.containsKey(deviceAction.name)) {
            throw DeviceConfigurationException("Action ${deviceAction.name} is already registered.")
        }
        actionMap[deviceAction.name] = deviceAction
        return deviceAction
    }

    /**
     * Registers a DeviceState associated with this component.
     */
    public fun <T, S : DeviceState<T>> registerState(name: String, state: S): S {
        if (stateMap.containsKey(name)) {
            throw DeviceConfigurationException("State $name is already registered.")
        }
        stateMap[name] = state
        return state
    }

    /**
     * Creates a property descriptor.
     */
    private fun createPropertyDescriptor(
        propertyName: String,
        converter: MetaConverter<*>,
        mutable: Boolean,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit
    ): PropertyDescriptor = propertyDescriptor(propertyName) {
        this.mutable = mutable
        converter.descriptor?.let { conv -> metaDescriptor { from(conv) } }
        descriptorBuilder()
    }

    /**
     * Creates an action descriptor.
     */
    private fun createActionDescriptor(
        actionName: String,
        inputConverter: MetaConverter<*>,
        outputConverter: MetaConverter<*>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit
    ): ActionDescriptor = actionDescriptor(actionName) {
        inputConverter.descriptor?.let { convIn -> inputMeta { from(convIn) } }
        outputConverter.descriptor?.let { convOut -> outputMeta { from(convOut) } }
        descriptorBuilder()
    }

    override fun <T> property(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptor(
                propertyName, converter, mutable = false, descriptorBuilder
            )
            val devProp = registerProperty(object : DevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = descriptor
                override val converter: MetaConverter<T> = converter
                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }
            })
            ReadOnlyProperty { _, _ -> devProp }
        }

    override fun <T> mutableProperty(
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit,
        name: String?,
        read: suspend D.(propertyName: String) -> T?,
        write: suspend D.(propertyName: String, value: T) -> Unit
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, MutableDevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val propertyName = name ?: property.name
            val descriptor = createPropertyDescriptor(
                propertyName, converter, mutable = true, descriptorBuilder
            )
            val devProp = registerProperty(object : MutableDevicePropertySpec<D, T> {
                override val descriptor: PropertyDescriptor = descriptor
                override val converter: MetaConverter<T> = converter
                override suspend fun read(device: D): T? =
                    withContext(device.coroutineContext) { device.read(propertyName) }

                override suspend fun write(device: D, value: T) =
                    withContext(device.coroutineContext) { device.write(propertyName, value) }
            })
            ReadOnlyProperty { _, _ -> devProp }
        }

    /**
     * Declares a child specification, using [fallbackSpec] or retrieving from [registry].
     *
     * @param fallbackSpec The default spec if not found in registry.
     * @param specKeyInRegistry Optional registry key.
     * @param childDeviceName Optional explicit name for the child device.
     * @param metaBuilder Optional lambda to build child [Meta].
     * @param configBuilder Lambda to configure the child's [DeviceLifecycleConfig].
     */
    public fun <CDS : CompositeControlComponentSpec<CD>, CD : ConfigurableCompositeControlComponent<CD>> childConfig(
        fallbackSpec: CDS,
        specKeyInRegistry: Name? = null,
        childDeviceName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {}
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, ChildComponentConfig<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            val registryKey = specKeyInRegistry ?: property.name.asName()
            val cName = childDeviceName ?: property.name.asName()
            val config = DeviceLifecycleConfigBuilder().apply(configBuilder).build()
            val meta = metaBuilder?.let { Meta(it) }
            val fromRegistry: CompositeControlComponentSpec<CD>? = thisRef.registry?.getSpec(registryKey)
            val foundSpec: CompositeControlComponentSpec<CD> = fromRegistry ?: fallbackSpec
            val mapKey = cName.toString()

            if (thisRef.childConfigMap.containsKey(mapKey)) {
                throw DeviceConfigurationException("Child config $mapKey already registered.")
            }

            val childConfig = object : ChildComponentConfig<CD> {
                override val spec: CompositeControlComponentSpec<CD> = foundSpec
                override val config: space.kscience.controls.spec.config.DeviceLifecycleConfig = config
                override val meta: Meta? = meta
                override val name: Name = cName
            }

            thisRef.childConfigMap[mapKey] = childConfig
            ReadOnlyProperty { _, _ -> childConfig }
        }

    /**
     * Declares child component configuration with direct specification.
     */
    public fun <CD : ConfigurableCompositeControlComponent<CD>> childConfig(
        spec: CompositeControlComponentSpec<CD>,
        name: Name,
        configBuilder: ChildComponentConfigBuilder<CD>.() -> Unit
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, ChildComponentConfig<CD>>> =
        PropertyDelegateProvider { thisRef, _ ->
            val builder = ChildComponentConfig.builder(spec, name).apply(configBuilder)
            val childComponentConfig = builder.build()
            val mapKey = name.toString()

            if (thisRef.childConfigMap.containsKey(mapKey)) {
                throw DeviceConfigurationException("Child config $mapKey already registered.")
            }

            thisRef.childConfigMap[mapKey] = childComponentConfig
            ReadOnlyProperty { _, _ -> childComponentConfig }
        }

    /**
     * Declares child component, returning only [CompositeControlComponentSpec].
     */
    public fun <CDS : CompositeControlComponentSpec<CD>, CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        fallbackSpec: CDS,
        specKeyInRegistry: Name? = null,
        childDeviceName: Name? = null,
        metaBuilder: (MutableMeta.() -> Unit)? = null,
        configBuilder: DeviceLifecycleConfigBuilder.() -> Unit = {}
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            val delegate = thisRef.childConfig(fallbackSpec, specKeyInRegistry, childDeviceName, metaBuilder, configBuilder)
                .provideDelegate(thisRef, property)

            ReadOnlyProperty { _, _ -> delegate.getValue(thisRef, property).spec }
        }

    /**
     * Declares child component with direct specification, returning only [CompositeControlComponentSpec].
     */
    public fun <CD : ConfigurableCompositeControlComponent<CD>> childSpec(
        spec: CompositeControlComponentSpec<CD>,
        name: Name,
        configBuilder: ChildComponentConfigBuilder<CD>.() -> Unit
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeControlComponentSpec<D>, CompositeControlComponentSpec<CD>>> =
        PropertyDelegateProvider { thisRef, property ->
            val delegate = thisRef.childConfig(spec, name, configBuilder).provideDelegate(thisRef, property)
            ReadOnlyProperty { _, _ -> delegate.getValue(thisRef, property).spec }
        }

    override fun <I, O> action(
        inputConverter: MetaConverter<I>,
        outputConverter: MetaConverter<O>,
        descriptorBuilder: ActionDescriptorBuilder.() -> Unit,
        name: String?,
        execute: suspend D.(input: I) -> O
    ): PropertyDelegateProvider<CompositeDeviceSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DeviceActionSpec<D, I, O>>> =
        PropertyDelegateProvider { _, property ->
            val actionName = name ?: property.name
            val descriptor = createActionDescriptor(
                actionName, inputConverter, outputConverter, descriptorBuilder
            )
            val devAction = registerAction(object : DeviceActionSpec<D, I, O> {
                override val descriptor: ActionDescriptor = descriptor
                override val inputConverter: MetaConverter<I> = inputConverter
                override val outputConverter: MetaConverter<O> = outputConverter

                override suspend fun execute(device: D, input: I): O = try {
                    withContext(device.coroutineContext) { device.execute(input) }
                } catch (ex: Exception) {
                    device.logger.error(ex) { "Error executing action $actionName on ${device.id}" }
                    throw ex
                }
            })

            ReadOnlyProperty { _, _ -> devAction }
        }

    /**
     * Registers a [DeviceState] that will be linked to a property.
     */
    public fun <T> stateProperty(
        state: DeviceState<T>,
        converter: MetaConverter<T>,
        descriptorBuilder: PropertyDescriptorBuilder.() -> Unit = {},
        name: String? = null
    ): PropertyDelegateProvider<CompositeControlComponentSpec<D>, ReadOnlyProperty<CompositeDeviceSpec<D>, DevicePropertySpec<D, T>>> =
        PropertyDelegateProvider { _, property ->
            val propertyName = name ?: property.name

            // Ensure the state is registered under the property's name if not already or if different.
            if (!stateMap.containsKey(propertyName) || stateMap[propertyName] !== state) {
                registerState(propertyName, state)
            }

            val descriptor = createPropertyDescriptor(propertyName, converter, state is MutableDeviceState<*>, descriptorBuilder)

            val devProp = if (state is MutableDeviceState<T>) {
                registerProperty(object : MutableDevicePropertySpec<D, T> {
                    override val descriptor: PropertyDescriptor = descriptor
                    override val converter: MetaConverter<T> = converter
                    override suspend fun read(device: D): T? = state.value
                    override suspend fun write(device: D, value: T) { state.value = value }
                })
            } else {
                registerProperty(object : DevicePropertySpec<D, T> {
                    override val descriptor: PropertyDescriptor = descriptor
                    override val converter: MetaConverter<T> = converter
                    override suspend fun read(device: D): T? = state.value
                })
            }

            ReadOnlyProperty { _, _ -> devProp }
        }
}


/**
 * Abstract base class for specifying a [ConfigurableCompositeControlComponent].
 * This simplifies creating specs for concrete composite device types by handling the factory.
 *
 * @param D The device type.
 * @property deviceFactory Factory function to create the device instance.
 */
public abstract class DeviceSpecification<D : ConfigurableCompositeControlComponent<D>>(
    public val deviceFactory: (Context, Meta) -> D
) : CompositeControlComponentSpec<D>()