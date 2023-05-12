package space.kscience.controls.ports

import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.string

/**
 * A DataForge plugin for managing ports
 */
public class Ports : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    private val portFactories by lazy {
        context.gather<PortFactory>(PortFactory.TYPE)
    }

    private val portCache = mutableMapOf<Meta, Port>()

    /**
     * Create a new [Port] according to specification
     */
    public fun buildPort(meta: Meta): Port = portCache.getOrPut(meta) {
        val type by meta.string { error("Port type is not defined") }
        val factory = portFactories.values.firstOrNull { it.type == type }
            ?: error("Port factory for type $type not found")
        factory.build(context, meta)
    }

    public companion object : PluginFactory<Ports> {

        override val tag: PluginTag = PluginTag("controls.ports", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): Ports = Ports()

    }
}