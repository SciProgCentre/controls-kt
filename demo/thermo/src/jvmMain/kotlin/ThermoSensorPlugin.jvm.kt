package center.sciprog.controls.demo.thermo

import kotlinx.serialization.modules.SerializersModule
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.visionforge.VisionPlugin


public actual class ThermoSensorPlugin : VisionPlugin() {
    actual override val tag: PluginTag get() = Companion.tag

    actual override val visionSerializersModule: SerializersModule get() = thermoVisionSerializersModule

    public actual companion object : PluginFactory<ThermoSensorPlugin> {
        actual override val tag: PluginTag = PluginTag("controls.vision.thermo")

        actual override fun build(context: Context, meta: Meta): ThermoSensorPlugin = ThermoSensorPlugin()
    }
}