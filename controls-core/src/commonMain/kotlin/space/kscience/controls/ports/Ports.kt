package space.kscience.controls.ports

import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.string

/**
 * A DataForge plugin for managing ports
 */
public class Ports : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    private val synchronousPortFactories by lazy {
        context.gather<Factory<SynchronousPort>>(SYNCHRONOUS_PORT_TYPE)
    }

    private val asynchronousPortFactories by lazy {
        context.gather<Factory<AsynchronousPort>>(ASYNCHRONOUS_PORT_TYPE)
    }

    /**
     * Create a new [AsynchronousPort] according to specification
     */
    public fun buildAsynchronousPort(meta: Meta): AsynchronousPort {
        val type by meta.string { error("Port type is not defined") }
        val factory = asynchronousPortFactories.entries
            .firstOrNull { it.key.toString() == type }?.value
            ?: error("Port factory for type $type not found")
        return factory.build(context, meta)
    }

    /**
     * Create a [SynchronousPort] according to specification or wrap an asynchronous implementation
     */
    public fun buildSynchronousPort(meta: Meta): SynchronousPort {
        val type by meta.string { error("Port type is not defined") }
        val factory = synchronousPortFactories.entries
            .firstOrNull { it.key.toString() == type }?.value
            ?: return buildAsynchronousPort(meta).asSynchronousPort()
        return factory.build(context, meta)
    }

    public companion object : PluginFactory<Ports> {

        override val tag: PluginTag = PluginTag("controls.ports", group = PluginTag.DATAFORGE_GROUP)

        public const val ASYNCHRONOUS_PORT_TYPE: String = "controls.asynchronousPort"
        public const val SYNCHRONOUS_PORT_TYPE: String = "controls.synchronousPort"

        override fun build(context: Context, meta: Meta): Ports = Ports()

    }
}