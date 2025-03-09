package space.kscience.controls.vision

import kotlinx.serialization.modules.SerializersModule
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.visionforge.VisionPlugin
import space.kscience.visionforge.html.ElementVisionRenderer


private val indicatorRenderer = ElementVisionRenderer<IndicatorVision> { name, vision: IndicatorVision, meta ->
//    val ledSize = vision.properties["size"].int ?: 15
//    val color = vision.color ?: "LightGray"
//    div("controls-indicator") {
//        style = """
//
//            @keyframes blink {
//              0%   { box-shadow: 0 0 10px; }
//              50%  { box-shadow: 0 0 30px; }
//              100% { box-shadow: 0 0 10px; }
//            }
//
//            display: inline-block;
//            margin: ${ledSize}px;
//            width: ${ledSize}px;
//            height: ${ledSize}px;
//            border-radius: 50%;
//
//            background: $color;
//            border: 1px solid darken($color,5%);
//            color: $color;
//            animation: blink 3s infinite;
//        """.trimIndent()
//    }
}

private val sliderRenderer = ElementVisionRenderer<SliderVision> { name, vision: SliderVision, meta ->

}


public actual class ControlVisionPlugin : VisionPlugin() {
    actual override val tag: PluginTag get() = Companion.tag

    actual override val visionSerializersModule: SerializersModule get() = controlsVisionSerializersModule

    override fun content(target: String): Map<Name, Any> = when (target) {
        ElementVisionRenderer.TYPE -> mapOf(
            "indicator".asName() to indicatorRenderer,
            "slider".asName() to sliderRenderer
        )

        else -> super.content(target)
    }

    public actual companion object : PluginFactory<ControlVisionPlugin> {
        actual override val tag: PluginTag = PluginTag("controls.vision")

        actual override fun build(context: Context, meta: Meta): ControlVisionPlugin = ControlVisionPlugin()

    }
}