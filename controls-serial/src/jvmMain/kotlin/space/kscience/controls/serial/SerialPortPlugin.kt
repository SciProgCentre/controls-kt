package space.kscience.controls.serial

import space.kscience.controls.ports.Ports
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

public class SerialPortPlugin : AbstractPlugin() {

    public val ports: Ports by require(Ports)

    override val tag: PluginTag get() = Companion.tag

    override fun content(target: String): Map<Name, Any> = when (target) {
        Ports.ASYNCHRONOUS_PORT_TYPE -> mapOf(
            Name.of("serial") to AsynchronousSerialPort,
        )

        Ports.SYNCHRONOUS_PORT_TYPE -> mapOf(
            Name.of("serial") to SynchronousSerialPort,
        )

        else -> emptyMap()
    }

    public companion object : PluginFactory<SerialPortPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.serial", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): SerialPortPlugin = SerialPortPlugin()

    }

}