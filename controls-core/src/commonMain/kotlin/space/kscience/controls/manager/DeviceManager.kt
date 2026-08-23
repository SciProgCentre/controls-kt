package space.kscience.controls.manager

import kotlinx.coroutines.launch
import space.kscience.controls.api.*
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.validate
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.last
import space.kscience.dataforge.names.parseAsName
import kotlin.properties.ReadOnlyProperty

/**
 * DataForge Context plugin that allows to manage devices locally
 */
public class DeviceManager : AbstractPlugin(), DeviceTree {
    override val tag: PluginTag get() = Companion.tag

    override val device: Device? get() = null

    /**
     * Device factories available in the context
     */
    public val factories: Map<Name, DeviceTreeFactory> by lazy {
        context.gather(DEVICE_FACTORY_TARGET, DeviceTreeFactory::class)
    }

    /**
     * Resolve device factory using full name or last segment of factory name if factory by full name is not found
     */
    public fun resolveDeviceFactory(type: String): DeviceTreeFactory? = factories[type.parseAsName()]
        ?: factories.mapKeys { it.key.last().toString() }[type]

    /**
     * Actual list of connected devices
     */
    override val children: Map<String, DeviceTree>
        field = HashMap<String, DeviceTree>()

    public fun registerDeviceTree(name: String, tree: DeviceTree) {
        children[name] = tree
    }

    /**
     * Register a device with the given name and device instance
     */
    public fun registerDevice(name: String, device: Device) {
        if (device is DeviceTree) {
            registerDeviceTree(name, device)
        } else {
            registerDeviceTree(name, DeviceTree(device))
        }
    }

    public companion object : PluginFactory<DeviceManager> {
        override val tag: PluginTag = PluginTag("devices", group = PluginTag.DATAFORGE_GROUP)

        public const val DEVICE_FACTORY_TARGET: String = "deviceFactory"

        override fun build(context: Context, meta: Meta): DeviceManager = DeviceManager()
    }
}

/**
 * Register and start device with given name
 */
public fun <D : Device> DeviceManager.install(name: String, device: D): D {
    registerDevice(name, device)
    device.launch {
        device.start()
    }
    return device
}

/**
 * Register and start device tree with given name
 */
public fun <DT : DeviceTree> DeviceManager.installTree(name: String, node: DT): DT {
    registerDeviceTree(name, node)

    fun DeviceTree.start() {
        context.launch {
            device?.start()
            children.values.forEach { it.start() }
        }
    }

    node.start()

    return node
}

/**
 * Install the device using its default id as name
 */
public fun <D : Device> DeviceManager.install(device: D): D = install(device.id, device)

/**
 * Install the in context's [DeviceManager]
 */
public fun <D : Device> Context.install(name: String, device: D): D = request(DeviceManager).install(name, device)

/**
 * Install the device tree in context's [DeviceManager]
 */
public fun <DT : DeviceTree> Context.installTree(name: String, tree: DT): DT =
    request(DeviceManager).installTree(name, tree)

/**
 * Install the device in context's [DeviceManager] using it default id as name
 */
public fun <D : Device> Context.install(device: D): D = request(DeviceManager).install(device.id, device)

/**
 * Register and start a device built by [factory] with current [Context] and [meta].
 */
public fun <D : Device> DeviceManager.install(
    name: String,
    factory: Factory<D>,
    meta: Meta = Meta.EMPTY
): D = install(name, factory(meta, context))

public fun DeviceManager.install(
    name: String,
    factory: DeviceFactory,
    meta: Meta = Meta.EMPTY
): Device = install(name, factory.buildDevice(context, meta))

public fun <DT : DeviceTree> DeviceManager.installTree(
    name: String,
    factory: Factory<DT>,
    meta: Meta = Meta.EMPTY
): DT = installTree(name, factory(meta, context))

/**
 * A delegate that initializes device on the first use
 */
public inline fun <D : DeviceTree> DeviceManager.installing(
    factory: Factory<D>,
    builder: MutableMeta.() -> Unit = {},
): ReadOnlyProperty<Any?, D> {
    val meta = Meta(builder)
    return ReadOnlyProperty { _, property ->
        val name = property.name
        val current = children[name]?.device
        if (current == null) {
            installTree(name, factory, meta)
        } else if (current.meta != meta) {
            error("Meta mismatch. Current device meta: ${current.meta}, but factory meta is $meta")
        } else {
            @Suppress("UNCHECKED_CAST")
            current as D
        }
    }
}

/**
 * Create (but not start or attach) a device using given [configuration] and registered factories
 *
 * @param additionalFactories additional factories to use when creating the device when they are not defined in the context
 */
public fun DeviceManager.createDeviceTree(
    configuration: Meta,
    additionalFactories: Map<String, DeviceTreeFactory> = emptyMap()
): DeviceTree {
    DeviceLibraryMetaSpec.validate(configuration)
    val type = configuration[DeviceLibraryMetaSpec.type] ?: error("Device type is not specified")
    val parameters = configuration[DeviceLibraryMetaSpec.parameters] ?: Meta.EMPTY
    val factory = additionalFactories[type]
        ?: resolveDeviceFactory(type)
        ?: error("Device type $type is not registered")
    return factory(parameters, context)

}


/**
 * Create and install a device using given [configuration] and registered factories
 */
public fun DeviceManager.install(
    configuration: Meta,
    additionalFactories: Map<String, DeviceTreeFactory> = emptyMap()
): DeviceTree {
    val name = configuration[DeviceLibraryMetaSpec.name] ?: error("Device name is not specified")
    return installTree(name, createDeviceTree(configuration, additionalFactories))
}
