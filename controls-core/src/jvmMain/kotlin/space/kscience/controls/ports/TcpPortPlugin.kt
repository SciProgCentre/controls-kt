package space.kscience.controls.ports

import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

public class TcpPortPlugin : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    override fun content(target: String): Map<Name, Any> = when(target){
        PortFactory.TYPE -> mapOf(Name.EMPTY to TcpPort)
        else -> emptyMap()
    }

    public companion object : PluginFactory<TcpPortPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.tcp", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): TcpPortPlugin = TcpPortPlugin()

    }

}