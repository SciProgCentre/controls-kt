package space.kscience.controls.vision

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.visionforge.Vision
import space.kscience.visionforge.VisionPlugin

public expect class ControlVisionPlugin: VisionPlugin{
    override val tag: PluginTag
    override val visionSerializersModule: SerializersModule
    public companion object: PluginFactory<ControlVisionPlugin>{
        override val tag: PluginTag
        override fun build(context: Context, meta: Meta): ControlVisionPlugin
    }
}

internal val controlsVisionSerializersModule = SerializersModule {
    polymorphic(Vision::class) {
        subclass(IndicatorVision.serializer())
        subclass(SliderVision.serializer())
    }
}