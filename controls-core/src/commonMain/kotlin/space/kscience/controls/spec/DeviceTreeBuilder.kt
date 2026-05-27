package space.kscience.controls.spec

import space.kscience.controls.api.DeviceTree
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta

/**
 * A builder for [DeviceTree]
 */
public class DeviceTreeBuilder : Factory<DeviceTree> {


    private var root: DeviceBuilder? = null

    private val children: MutableMap<String, DeviceTreeBuilder> = mutableMapOf()

    /**
     * Register a device hub builder with the given name.
     */
    public fun tree(name: String, builder: DeviceTreeBuilder) {
        require(!name.isEmpty()) { "Device name cannot be empty" }
        require(name !in children.keys) { "Device $name is already defined" }
        children[name] = builder
    }

    /**
     * Register a device builder with the given name.
     */
    public fun device(name: String, builder: DeviceBuilder) {
        tree(name, DeviceTreeBuilder().apply { root = builder })
    }

    /**
     * Register a device builder with the given name.
     */
    public fun device(name: String, builder: DeviceBuilder.() -> Unit) {
        val device = DeviceBuilder().apply(builder)
        device(name, device)
    }

    /**
     * Register a device hub builder with the given name.
     */
    public fun tree(name: String, builder: DeviceTreeBuilder.() -> Unit) {
        tree(name, DeviceTreeBuilder().apply(builder))
    }


    public fun root(builder: DeviceBuilder): Unit {
        root = builder
    }

    public fun root(builder: DeviceBuilder.() -> Unit): Unit = root(DeviceBuilder().apply(builder))

    /**
     * Build a [DeviceTree] from the registered devices.
     */
    override fun build(context: Context, meta: Meta): DeviceTree = DeviceTree(
        rootDevice = root?.build(context, meta),
        children = children.mapValues { it.value.build(context, meta) }
    )


}

/**
 * Creates a DeviceHub using the provided context and optional meta, with the specified builder configuration.
 */
public fun DeviceTree(context: Context, meta: Meta = Meta.EMPTY, builder: DeviceTreeBuilder.() -> Unit): DeviceTree =
    DeviceTreeBuilder().apply(builder).build(context, meta)
