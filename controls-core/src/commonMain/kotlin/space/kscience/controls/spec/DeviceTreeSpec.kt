package space.kscience.controls.spec

import space.kscience.controls.api.DeviceElementDescriptor
import space.kscience.controls.api.DeviceTree
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.plus

/**
 * A specification for a device tree structure.
 */
public interface DeviceTreeSpec {
    public val deviceSpec: DeviceSpec? get() = null
    public val childrenSpecs: Map<String, DeviceTreeSpec>
}

/**
 * Checks for missing elements in a given device tree based on the specifications
 * defined in the `DeviceTreeSpec`.
 *
 * This method iterates through the device tree specification and compares it
 * against the provided `DeviceTree` to identify any elements that are specified
 * in the spec but are missing in the corresponding device tree nodes.
 *
 * @param deviceTree The `DeviceTree` instance to check for missing elements.
 *                   Can be `null` if no device tree exists for the specification.
 * @return A map where the keys represent hierarchical names of missing elements
 *         (or the root if the name is empty), and the values are sets of
 *         `DeviceElementDescriptor` instances that are missing for the corresponding
 *         part of the tree.
 */
public fun DeviceTreeSpec.checkMissingElements(deviceTree: DeviceTree?): Map<Name, Set<DeviceElementDescriptor>> =
    buildMap {
        deviceSpec?.let { device ->
            if (deviceTree?.device == null) {
                put(Name.EMPTY, device.elementDescriptors)
            }
        }
        childrenSpecs.forEach { (name, childSpec) ->
            val childDeviceTree = deviceTree?.children?.get(name)
            val childMissingDescriptors = childSpec.checkMissingElements(childDeviceTree)
            if (childMissingDescriptors.isNotEmpty()) {
                putAll(childMissingDescriptors.mapKeys { Name.of(name) + it.key })
            }
        }
    }

/**
 * Verify if this device tree adheres to the specification
 */
public fun DeviceTreeSpec.verify(deviceTree: DeviceTree): Boolean = checkMissingElements(deviceTree).isEmpty()

/**
 * Create a device tree specification from a device and children
 */
public fun DeviceTreeSpec(
    device: DeviceSpec? = null,
    children: Map<String, DeviceTreeSpec> = emptyMap()
): DeviceTreeSpec = object : DeviceTreeSpec {
    override val deviceSpec: DeviceSpec? get() = device
    override val childrenSpecs: Map<String, DeviceTreeSpec> get() = children
}
