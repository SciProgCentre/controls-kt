package center.sciprog.controls.demo.thermo

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.visionforge.Vision
import space.kscience.visionforge.VisionPlugin


public expect class ThermoSensorPlugin: VisionPlugin{
    override val tag: PluginTag
    override val visionSerializersModule: SerializersModule

    public companion object: PluginFactory<ThermoSensorPlugin>{
        override val tag: PluginTag
        override fun build(context: Context, meta: Meta): ThermoSensorPlugin
    }
}

internal val thermoVisionSerializersModule = SerializersModule {
    polymorphic(Vision::class) {
        subclass(VisionOfThermoSensorHub.serializer())
    }
}