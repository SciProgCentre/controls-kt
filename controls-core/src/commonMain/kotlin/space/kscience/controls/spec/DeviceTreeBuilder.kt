package space.kscience.controls.spec

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.descriptors.node
import space.kscience.dataforge.meta.get

/**
 * A builder for [DeviceTree]
 */
public class DeviceTreeBuilder : DeviceTreeFactory, DeviceTreeSpec {


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

    override val descriptor: MetaDescriptor
        get() = MetaDescriptor {
            root?.descriptor?.let { node("root", it) }
            this@DeviceTreeBuilder.children.forEach { (name, builder) ->
                node("child[$name]", builder.descriptor)
            }
        }

    /**
     * Build a [DeviceTree] from the registered devices.
     */
    override fun build(context: Context, meta: Meta): DeviceTree = DeviceTree(
        rootDevice = root?.buildDevice(context, meta["root"] ?: Meta.EMPTY),
        children = children.mapValues { it.value.build(context, meta["child[${it.key}]"] ?: Meta.EMPTY) }
    )

    override val deviceSpec: DeviceSpec? get() = root?.buildDeviceSpec()

    override val childrenSpecs: Map<String, DeviceTreeSpec> get() = children
}

/**
 * Creates a DeviceHub using the provided context and optional meta, with the specified builder configuration.
 */
public fun DeviceTree(context: Context, meta: Meta = Meta.EMPTY, builder: DeviceTreeBuilder.() -> Unit): DeviceTree =
    DeviceTreeBuilder().apply(builder).build(context, meta)
