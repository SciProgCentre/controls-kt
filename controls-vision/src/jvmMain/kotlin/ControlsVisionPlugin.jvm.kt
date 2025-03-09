package space.kscience.controls.vision

import kotlinx.serialization.modules.SerializersModule
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.visionforge.VisionPlugin

public actual class ControlVisionPlugin : VisionPlugin() {
    actual override val tag: PluginTag get() = Companion.tag

    actual override val visionSerializersModule: SerializersModule get() = controlsVisionSerializersModule

    public actual companion object : PluginFactory<ControlVisionPlugin> {
        actual override val tag: PluginTag = PluginTag("controls.vision")

        actual override fun build(context: Context, meta: Meta): ControlVisionPlugin = ControlVisionPlugin()

    }
}