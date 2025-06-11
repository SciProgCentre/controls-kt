package center.sciprog.controls.demo.thermo

import androidx.compose.runtime.DisposableEffect
import app.softwork.bootstrapcompose.Column
import app.softwork.bootstrapcompose.Row
import kotlinx.serialization.modules.SerializersModule
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.plotly.PlotlyPlugin
import space.kscience.visionforge.VisionPlugin
import space.kscience.visionforge.html.ComposeHtmlVisionRenderer
import space.kscience.visionforge.html.ElementVisionRenderer


val thermoSensorHubRenderer =
    ComposeHtmlVisionRenderer<VisionOfThermoSensorHub> { name, vision: VisionOfThermoSensorHub, meta ->
        Row {
            Column(size = 3) {
                vision.positions.forEach { state: ThermoSensorVisionState ->
                    Row {

                    }
                }
            }

            Column(size = 9) {
                DisposableEffect(Unit){
                    scopeElement
                }
            }
        }
    }


public actual class ThermoSensorPlugin : VisionPlugin() {
    val plotly by require(PlotlyPlugin)

    actual override val tag: PluginTag get() = Companion.tag

    actual override val visionSerializersModule: SerializersModule get() = thermoVisionSerializersModule

    override fun content(target: String): Map<Name, Any> = when (target) {
        ElementVisionRenderer.TYPE -> mapOf(
            "thermo".asName() to thermoSensorHubRenderer,
        )

        else -> super.content(target)
    }

    public actual companion object : PluginFactory<ThermoSensorPlugin> {
        actual override val tag: PluginTag = PluginTag("controls.vision")

        actual override fun build(context: Context, meta: Meta): ThermoSensorPlugin = ThermoSensorPlugin()

    }
}