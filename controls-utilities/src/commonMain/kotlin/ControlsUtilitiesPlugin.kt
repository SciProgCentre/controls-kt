package space.kscience.controls.utilities

import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

public class ControlsUtilitiesPlugin : AbstractPlugin() {
    public val deviceManager: DeviceManager by require(DeviceManager)

    override val tag: PluginTag get() = Companion.tag


    override fun content(target: String): Map<Name, Any> = when (target) {
        DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(
            Name.of("alarm") to Alarm
        )
        else -> super.content(target)
    }

    public companion object : PluginFactory<ControlsUtilitiesPlugin> {
        override val tag: PluginTag = PluginTag("controls.utilities")

        override fun build(
            context: Context,
            meta: Meta
        ): ControlsUtilitiesPlugin = ControlsUtilitiesPlugin()

    }
}