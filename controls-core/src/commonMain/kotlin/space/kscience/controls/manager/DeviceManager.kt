package space.kscience.controls.manager

import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.id
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.names.Name
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
    @Suppress("UNCHECKED_CAST")
    public val factories: Map<Name, Factory<Device>> by lazy {
        context.gather(DEVICE_FACTORY_TARGET, Factory::class) as Map<Name, Factory<Device>>
    }

    /**
     * Actual list of connected devices
     */
    private val _children = HashMap<String, DeviceTree>()
    override val children: Map<String, DeviceTree> get() = _children

    public fun registerDeviceTree(name: String, tree: DeviceTree) {
        _children[name] = tree
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

public fun DeviceManager.installTree(
    name: String,
    factory: Factory<DeviceTree>,
    meta: Meta = Meta.EMPTY
): DeviceTree = installTree(name, factory(meta, context))

/**
 * A delegate that initializes device on the first use
 */
public inline fun <D : Device> DeviceManager.installing(
    factory: Factory<D>,
    builder: MutableMeta.() -> Unit = {},
): ReadOnlyProperty<Any?, D> {
    val meta = Meta(builder)
    return ReadOnlyProperty { _, property ->
        val name = property.name
        val current = children[name]?.device
        if (current == null) {
            install(name, factory, meta)
        } else if (current.meta != meta) {
            error("Meta mismatch. Current device meta: ${current.meta}, but factory meta is $meta")
        } else {
            @Suppress("UNCHECKED_CAST")
            current as D
        }
    }
}