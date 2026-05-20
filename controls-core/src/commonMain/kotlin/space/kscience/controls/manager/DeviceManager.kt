package space.kscience.controls.manager

import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.id
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import kotlin.properties.ReadOnlyProperty

/**
 * DataForge Context plugin that allows to manage devices locally
 */
public class DeviceManager : AbstractPlugin(), DeviceTree {
    override val tag: PluginTag get() = Companion.tag

    override val device: Device? get() = null

    /**
     * Actual list of connected devices
     */
    private val _devices = HashMap<String, DeviceTree>()
    override val children: Map<String, DeviceTree> get() = _devices

    public fun registerNode(name: String, hub: DeviceTree) {
        _devices[name] = hub
    }

    public fun registerDevice(name: String, device: Device) {
        registerNode(name, DeviceTree(device))
    }

    public companion object : PluginFactory<DeviceManager> {
        override val tag: PluginTag = PluginTag("devices", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): DeviceManager = DeviceManager()
    }
}

public fun <D : Device> DeviceManager.install(name: String, device: D): D {
    registerDevice(name, device)
    device.launch {
        device.start()
    }
    return device
}

public fun <DN: DeviceTree> DeviceManager.installNode(name: String, node: DN): DN {
    registerNode(name, node)

    fun DeviceTree.start() {
        context.launch {
            device?.start()
            children.values.forEach { it.start() }
        }
    }

    node.start()

    return node
}

public fun <D : Device> DeviceManager.install(device: D): D = install(device.id, device)

public fun <D : Device> Context.install(name: String, device: D): D = request(DeviceManager).install(name, device)

public fun <DN: DeviceTree> Context.installNode(name: String, node: DN): DN = request(DeviceManager).installNode(name, node)

public fun <D : Device> Context.install(device: D): D = request(DeviceManager).install(device.id, device)

/**
 * Register and start a device built by [factory] with current [Context] and [meta].
 */
public fun <D : Device> DeviceManager.install(
    name: String,
    factory: Factory<D>,
    meta: Meta = Meta.EMPTY
): D = install(name, factory(meta, context))

public fun DeviceManager.installNode(
    name: String,
    factory: Factory<DeviceTree>,
    meta: Meta = Meta.EMPTY
): DeviceTree = installNode(name, factory(meta, context))

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