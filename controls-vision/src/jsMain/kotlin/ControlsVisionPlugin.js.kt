package space.kscience.controls.vision

import kotlinx.serialization.modules.SerializersModule
import org.w3c.dom.Element
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.visionforge.ElementVisionRenderer
import space.kscience.visionforge.Vision
import space.kscience.visionforge.VisionPlugin

public actual class ControlVisionPlugin : VisionPlugin(), ElementVisionRenderer {
    override val tag: PluginTag get() = Companion.tag

    override val visionSerializersModule: SerializersModule get() = controlsVisionSerializersModule

    override fun rateVision(vision: Vision): Int {
        TODO("Not yet implemented")
    }

    override fun render(element: Element, name: Name, vision: Vision, meta: Meta) {
        TODO("Not yet implemented")
    }

    public actual companion object : PluginFactory<ControlVisionPlugin> {
        override val tag: PluginTag = PluginTag("controls.vision")

        override fun build(context: Context, meta: Meta): ControlVisionPlugin = ControlVisionPlugin()

    }
}