package space.kscience.controls.pi

import space.kscience.controls.ports.Ports
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta

public class PiPlugin : AbstractPlugin() {
    public val ports: Ports by require(Ports)

    override val tag: PluginTag get() = Companion.tag

    public companion object : PluginFactory<PiPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.pi", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): PiPlugin = PiPlugin()

    }
}