package space.kscience.controls.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.*
import space.kscience.dataforge.provider.Provider

/**
 * A hub that could locate multiple devices and redirect actions to them
 */
public interface DeviceTree : Provider {

    public val device: Device?

    public val children: Map<String, DeviceTree>

    /**
     * The current device followed by replacements. Trees with a mutable root must override this method.
     */
    public fun deviceFlow(): Flow<Device?> = flow { emit(device) }

    /**
     * The current children followed by updated snapshots. Mutable trees must override this method.
     * Emitted maps must not change after emission.
     */
    public fun childrenFlow(): Flow<Map<String, DeviceTree>> = flow { emit(children.toMap()) }

    override val defaultTarget: String get() = Device.DEVICE_TARGET

    override val defaultChainTarget: String get() = Device.DEVICE_TARGET

    /**
     * Retrieves a map containing all descendant devices of the current hub.
     * Each entry in the map represents a device, where the key is the hierarchical name
     * of the device (built using the prefix for the current hub and its descendants),
     * and the value is the corresponding device instance.
     *
     * The method collects devices starting from the root device of the current hub,
     * recursively including all devices within child hubs. The hierarchical names in
     * the resulting map reflect the structure of the hub network.
     *
     * @return A map of descendant devices, where keys represent hierarchical names
     *         of devices, and values are the corresponding device instances.
     */
    public fun descendantDevices(): Map<Name, Device> = buildMap<Name, Device> {
        children.forEach { (name, node) ->
            val prefix = Name.of(name)
            node.device?.let { put(prefix, it) }
            putAll(node.descendantDevices().mapKeys { (key, _) -> prefix + key })
        }
    }

    override fun content(target: String): Map<Name, Any> = if (target == Device.DEVICE_TARGET) {
        descendantDevices()
    } else {
        emptyMap()
    }

    public companion object
}

/**
 * Create a device hub from a map of devices
 */
public fun DeviceTree(
    rootDevice: Device? = null,
    children: Map<String, DeviceTree> = emptyMap()
): DeviceTree = object : DeviceTree {
    override val device: Device? = rootDevice
    override val children: Map<String, DeviceTree> = children
}

/**
 * Resolve a device by its name (including recursion if needed). Throw an exception if the device is not found.
 */
public fun DeviceTree.resolveDevice(name: Name): Device = when (name.length) {
    0 -> device ?: error("Device tree is not a device. It could not be accessed with empty name")
    1 -> children[name.first().toString()]?.device ?: error("Device $name not found in $this")
    else -> children[name.first().toString()]?.resolveDevice(name.cutFirst())
        ?: error("Device ${name.toStringUnescaped()} not found in $this")
}

public fun DeviceTree.resolveDevice(name: String): Device = resolveDevice(name.parseAsName())

/**
 * Resolve a device by name or return null if device with given name is not found in the tree
 */
public fun DeviceTree.resolveDeviceOrNull(name: Name): Device? = when (name.length) {
    0 -> device
    1 -> children[name.first().toString()]?.device
    else -> children[name.first().toString()]?.resolveDeviceOrNull(name.cutFirst())
}

///**
// * Create a device hub from a map of devices including subdevices
// */
//public fun DeviceNode(devices: Map<Name, Device>): DeviceNode {
//    val rootDevice = devices[Name.EMPTY]
//    val children: Map<String, Map<Name, Device>> = buildMap {
//        devices.entries
//            .filter { !it.key.isEmpty() }
//            .groupBy { it.key.first() }
//            .map { (parentKey, entries) ->
//                TODO()
//            }
//    }
//    return DeviceNode(rootDevice, children.mapValues { DeviceNode(it.value) })
//}

public suspend fun DeviceTree.readProperty(deviceName: Name, propertyName: String): Meta =
    resolveDevice(deviceName).readProperty(propertyName)

public suspend fun DeviceTree.writeProperty(deviceName: Name, propertyName: String, value: Meta) {
    resolveDevice(deviceName).writeProperty(propertyName, value)
}

public suspend fun DeviceTree.execute(deviceName: Name, command: String, argument: Meta?): Meta? =
    resolveDevice(deviceName).execute(command, argument)


/**
 * Start all devices in the tree
 */
context(coroutineScope: CoroutineScope)
public fun DeviceTree.start(): Job = coroutineScope.launch {
    device?.start()
    children.values.forEach { it.start() }
}
