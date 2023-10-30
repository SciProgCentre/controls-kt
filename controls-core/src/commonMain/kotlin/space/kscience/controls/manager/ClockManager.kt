package space.kscience.controls.manager

import kotlinx.datetime.Clock
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta

public class ClockManager : AbstractPlugin() {
    override val tag: PluginTag get() = DeviceManager.tag

    public val clock: Clock by lazy {
        //TODO add clock customization
        Clock.System
    }

    public companion object : PluginFactory<ClockManager> {
        override val tag: PluginTag = PluginTag("clock", group = PluginTag.DATAFORGE_GROUP)

        override fun build(context: Context, meta: Meta): ClockManager = ClockManager()
    }
}

public val Context.clock: Clock get() = plugins[ClockManager]?.clock ?: Clock.System