package space.kscience.controls.ports

import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.reflect.KClass

public class KtorTcpPortPlugin : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    override fun content(target: String): Map<Name, Any> = when(target){
        PortFactory.TYPE -> mapOf(Name.EMPTY to KtorTcpPort)
        else -> emptyMap()
    }

    public companion object : PluginFactory<KtorTcpPortPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.serial", group = PluginTag.DATAFORGE_GROUP)

        override val type: KClass<out KtorTcpPortPlugin> = KtorTcpPortPlugin::class

        override fun build(context: Context, meta: Meta): KtorTcpPortPlugin = KtorTcpPortPlugin()

    }

}