package space.kscience.controls.pi

import com.pi4j.Pi4J
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.ports.Ports
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import com.pi4j.context.Context as PiContext

public class PiPlugin : AbstractPlugin() {
    public val ports: Ports by require(Ports)
    public val devices: DeviceManager by require(DeviceManager)

    override val tag: PluginTag get() = Companion.tag

    public val piContext: PiContext by lazy { createPiContext(context, meta) }

    override fun content(target: String): Map<Name, Any> = when (target) {
        Ports.ASYNCHRONOUS_PORT_TYPE -> mapOf(
            "serial".asName() to AsynchronousPiPort,
        )
        Ports.SYNCHRONOUS_PORT_TYPE -> mapOf(
            "serial".asName() to SynchronousPiPort,
        )

        else -> super.content(target)
    }

    override fun detach() {
        piContext.shutdown()
        super.detach()
    }

    public companion object : PluginFactory<PiPlugin> {

        override val tag: PluginTag = PluginTag("controls.ports.pi", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): PiPlugin = PiPlugin()

        @Suppress("UNUSED_PARAMETER")
        public fun createPiContext(context: Context, meta: Meta): PiContext = Pi4J.newAutoContext()

    }
}