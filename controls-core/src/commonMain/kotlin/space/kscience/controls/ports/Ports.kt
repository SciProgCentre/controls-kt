package space.kscience.controls.ports

import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.string
import kotlin.reflect.KClass

public class Ports : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    private val portFactories by lazy {
        context.gather<PortFactory>(PortFactory.TYPE)
    }

    private val portCache = mutableMapOf<Meta, Port>()

    public fun buildPort(meta: Meta): Port = portCache.getOrPut(meta) {
        val type by meta.string { error("Port type is not defined") }
        val factory = portFactories.values.firstOrNull { it.type == type }
            ?: error("Port factory for type $type not found")
        factory.build(context, meta)
    }

    public companion object : PluginFactory<Ports> {

        override val tag: PluginTag = PluginTag("controls.ports", group = PluginTag.DATAFORGE_GROUP)

        override val type: KClass<out Ports> = Ports::class

        override fun build(context: Context, meta: Meta): Ports = Ports()

    }
}