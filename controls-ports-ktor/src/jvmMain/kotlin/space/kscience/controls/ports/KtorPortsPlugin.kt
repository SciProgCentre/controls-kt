package space.kscience.controls.ports

import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName

public class KtorPortsPlugin : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    override fun content(target: String): Map<Name, Any> = when (target) {
        Ports.ASYNCHRONOUS_PORT_TYPE -> mapOf("tcp".asName() to KtorTcpPort, "udp".asName() to KtorUdpPort)
        else -> emptyMap()
    }

    public companion object : PluginFactory<KtorPortsPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.ktor", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): KtorPortsPlugin = KtorPortsPlugin()

    }

}