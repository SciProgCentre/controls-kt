package space.kscience.controls.serial

import space.kscience.controls.ports.PortFactory
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

public class SerialPortPlugin : AbstractPlugin() {

    override val tag: PluginTag get() = Companion.tag

    override fun content(target: String): Map<Name, Any> = when(target){
        PortFactory.TYPE -> mapOf(Name.EMPTY to SerialPort)
        else -> emptyMap()
    }

    public companion object : PluginFactory<SerialPortPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.serial", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): SerialPortPlugin = SerialPortPlugin()

    }

}