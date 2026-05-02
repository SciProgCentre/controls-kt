package space.kscience.controls.spec

import space.kscience.controls.api.DeviceHub
import space.kscience.controls.api.ParentDevice
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty

/**
 * A builder for [DeviceHub]
 */
public class DeviceHubBuilder : Factory<DeviceHub> {

    private val devices: MutableMap<Name, DeviceBuilder> = mutableMapOf()

    /**
     * Register a device builder with the given name.
     */
    public fun device(name: Name, builder: DeviceBuilder) {
        require(!name.isEmpty()) { "Device name cannot be empty" }
        require(name !in devices) { "Device $name is already defined" }

        devices[name] = builder
    }

    /**
     * Register a device builder with the given name.
     */
    public fun device(name: Name, builder: DeviceBuilder.() -> Unit) {
        val device = DeviceBuilder().apply(builder)
        device(name, device)
    }

    private var root: DeviceBuilder? = null

    public fun root(builder: DeviceBuilder): Unit {
        root = builder
    }

    public fun root(builder: DeviceBuilder.() -> Unit): Unit = root(DeviceBuilder().apply(builder))

    /**
     * Build a [DeviceHub] from the registered devices.
     */
    override fun build(context: Context, meta: Meta): DeviceHub = root?.let { root ->
        ParentDevice(root.build(context, meta), devices.mapValues { it.value.build(context, meta) })
    } ?: DeviceHub(devices.mapValues { it.value.build(context, meta) })

    /**
     * Build a [ParentDevice] from the registered devices. Throw an exception if the root device is not defined.
     */
    public fun buildParent(context: Context, meta: Meta): ParentDevice {
        return ParentDevice(
            rootDevice = (root ?: error("Root device is not defined")).build(context, meta),
            children = devices.mapValues { it.value.build(context, meta) })
    }
}

/**
 * Creates a DeviceHub using the provided context and optional meta, with the specified builder configuration.
 */
public fun DeviceHub(context: Context, meta: Meta = Meta.EMPTY, builder: DeviceHubBuilder.() -> Unit): DeviceHub =
    DeviceHubBuilder().apply(builder).build(context, meta)

/**
 * Creates a ParentDevice using the provided context and optional meta, with the specified builder configuration.
 */
public fun ParentDevice(context: Context, meta: Meta = Meta.EMPTY, builder: DeviceHubBuilder.() -> Unit): ParentDevice =
    DeviceHubBuilder().apply(builder).buildParent(context, meta)