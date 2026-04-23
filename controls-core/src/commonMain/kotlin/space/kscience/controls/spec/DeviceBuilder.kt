package space.kscience.controls.spec

import space.kscience.controls.api.CachingDevice
import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter

/**
 * A property specification with default value
 */
public class DevicePropertySpecWithDefault<T>(
    override val converter: MetaConverter<T>,
    override val descriptor: PropertyDescriptor,
    public val defaultValue: T,
) : DevicePropertySpec<T>

private class LogicalPropertyAccessor<T>(
    private val device: CachingDevice,
    override val spec: DevicePropertySpecWithDefault<T>,
) : PropertyReader<T>, PropertyWriter<T> {
    context(device: DeviceBase)
    override suspend fun read(): T = device.getCachedProperty(spec.name)
        ?.let { return spec.converter.read(it) }
        ?: spec.defaultValue

    context(device: DeviceBase)
    override suspend fun write(value: T) {
        @OptIn(InternalDeviceAPI::class)
        device.setCachedProperty(spec.name, spec.converter.convert(value))
    }
}

/**
 * A builder class for creating instances of [DeviceBase]. The [DeviceBuilder] class allows
 * configuration and registration of readers, writers, actions, and lifecycle hooks for the device.
 * It implements the [Factory<CachingDevice>] interface, enabling it to produce fully constructed `Device` instances.
 */
public class DeviceBuilder : Factory<CachingDevice> {

    private val buildReaders: MutableSet<PropertyReader<*>> = mutableSetOf()
    private val buildWriters: MutableSet<PropertyWriter<*>> = mutableSetOf()
    private val logicalProperties: MutableSet<DevicePropertySpecWithDefault<*>> = mutableSetOf()
    private val buildActions: MutableSet<ActionExecutor<*, *>> = mutableSetOf()

    /**
     * Register a property reader and corresponding property specification for the device.
     */
    public fun reader(reader: PropertyReader<*>) {
        buildReaders.add(reader)
    }

    /**
     * Register a property writer and corresponding property specification for the device.
     */
    public fun writer(writer: PropertyWriter<*>) {
        buildWriters.add(writer)
    }

    /**
     * Register a logical (no physical reader or writer) read/write property
     */
    public fun logical(spec: DevicePropertySpecWithDefault<*>): Unit {
        logicalProperties.add(spec)
    }

    /**
     * Register an action executor and corresponding action specification for the device.
     */
    public fun action(executor: ActionExecutor<*, *>) {
        buildActions.add(executor)
    }

    private var onStartFunction: suspend DeviceBase.() -> Unit = {}

    /**
     * Set on start function for the device. Only one function can be set.
     */
    public fun onStart(block: suspend DeviceBase.() -> Unit) {
        //TODO consider multiple onStart functions
        onStartFunction = block
    }

    private var onStopFunction: suspend DeviceBase.() -> Unit = {}

    /**
     * Set on stop function for the device. Only one function can be set.
     */
    public fun onStop(block: suspend DeviceBase.() -> Unit) {
        onStopFunction = block
    }

    /**
     * Build a fully configured [DeviceBase] instance from the registered readers, writers, and actions.
     */
    override fun build(context: Context, meta: Meta): DeviceBase = object : DeviceBase(context, meta) {

        //protective copy for all properties

        override val readers: Map<String, PropertyReader<*>> = logicalProperties.associate { spec ->
            spec.name to LogicalPropertyAccessor(device = this, spec = spec)
        } + buildReaders.associateBy { it.spec.name }

        override val writers: Map<String, PropertyWriter<*>> = logicalProperties.associate { spec ->
            spec.name to LogicalPropertyAccessor(device = this, spec = spec)
        } + buildWriters.associateBy { it.spec.name }

        override val actions: Map<String, ActionExecutor<*, *>> = buildActions.associateBy { it.spec.name }

        override suspend fun onStart() {
            super.onStart()
            onStartFunction()
        }

        override suspend fun onStop() {
            onStopFunction()
            super.onStop()
        }

        override fun toString(): String = "DeviceBuilder.DeviceBase(context=$context, meta=$meta)"
    }


    /**
     * Ensure that this builder fully implements the given specification. Throw an exception if it does not
     */
    public fun validateFor(spec: DeviceSpec) {
        spec.properties.forEach { (name, propertySpec) ->
            if (propertySpec.isReadable) {
                val reader = buildReaders.find { it.spec.name == propertySpec.name }
                check(reader?.spec == propertySpec) {
                    "Builder is expected to have reader for property $propertySpec, but ${reader?.descriptor} is found"
                }
            }
            if (propertySpec.isMutable) {
                val writer = buildWriters.find { it.spec.name == propertySpec.name }
                check(writer?.spec == propertySpec) {
                    "Builder is expected to have writer for property $propertySpec, but ${writer?.descriptor} is found"
                }
            }
        }
        spec.actions.forEach { (name, actionSpec) ->
            val actionExecutor = buildActions.find { it.spec.name == actionSpec.name }
            check(actionExecutor?.spec == actionSpec) {
                "Builder is expected to have action executor for action $actionSpec, but ${actionExecutor?.descriptor} is found"
            }
        }
    }
}

public fun <T> DeviceBuilder.logical(
    spec: DevicePropertySpec<T>,
    default: T
): Unit = logical(DevicePropertySpecWithDefault(spec.converter, spec.descriptor, default))

public fun <T> DeviceBuilder.logical(
    converter: MetaConverter<T>,
    descriptor: PropertyDescriptor,
    defaultValue: T
): Unit = logical(DevicePropertySpecWithDefault(converter, descriptor, defaultValue))

/**
 * Create a device with given [builder]
 */
public fun Device(
    context: Context,
    meta: Meta = Meta.EMPTY,
    builder: DeviceBuilder.() -> Unit
): Device = DeviceBuilder().apply(builder).build(context, meta)


public fun <T> DeviceBuilder.reader(spec: DevicePropertySpec<T>, read: suspend context(DeviceBase) () -> T) {
    check(spec.isReadable) { "Property ${spec.name} is not readable" }
    val reader = object : PropertyReader<T> {
        override val spec: DevicePropertySpec<T> = spec

        context(device: DeviceBase)
        override suspend fun read(): T = read()
    }
    reader(reader)
}

public fun <T> DeviceBuilder.writer(spec: DevicePropertySpec<T>, write: suspend context(DeviceBase) (T) -> Unit) {
    check(spec.isMutable) { "Property ${spec.name} is not mutable" }
    val writer = object : PropertyWriter<T> {
        override val spec: DevicePropertySpec<T> = spec

        context(device: DeviceBase)
        override suspend fun write(value: T) = write(value)
    }
    writer(writer)
}

public fun <I, O> DeviceBuilder.action(spec: DeviceActionSpec<I, O>, execute: suspend context(DeviceBase) (I) -> O) {
    val action = object : ActionExecutor<I, O> {
        override val spec: DeviceActionSpec<I, O> = spec

        context(device: DeviceBase)
        override suspend fun execute(input: I): O = execute(input)
    }
    action(action)
}