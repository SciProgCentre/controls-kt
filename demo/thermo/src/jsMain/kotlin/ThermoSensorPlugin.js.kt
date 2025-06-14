package center.sciprog.controls.demo.thermo

import app.softwork.bootstrapcompose.Color
import app.softwork.bootstrapcompose.Column
import app.softwork.bootstrapcompose.Row
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.compose.web.dom.Text
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.plotly.PlotlyJsPlugin
import space.kscience.visionforge.VisionPlugin
import space.kscience.visionforge.html.ComposeHtmlVisionRenderer
import space.kscience.visionforge.html.ElementVisionRenderer
import kotlin.math.floor


private fun Double.format2f(): String = (floor(this * 100) / 100).toString()

val thermoSensorHubRenderer =
    ComposeHtmlVisionRenderer<VisionOfThermoSensorHub> { name, vision: VisionOfThermoSensorHub, meta ->

        Row {
            Column(size = 3) {
                vision.sensorData.forEach { (sensorName, state) ->
                    Row(
                        styling = {
                            Background.color = when (state.status) {
                                ThermoSensorStatus.NotConnected -> Color.Dark
                                ThermoSensorStatus.Normal -> Color.Light
                                ThermoSensorStatus.Warning -> Color.Warning
                                ThermoSensorStatus.Alarm -> Color.Danger
                            }
                        }
                    ) {
                        Text(sensorName)
                        Text(state.temperature.format2f())

//                        Checkbox(
//                            checked = state.plotEnabled,
//                            label = "",
//                        ) {
//                            vision.asyncControlEvent()
////                            if (it) {
////                                plotEnabled.add(sensorName)
////                            } else {
////                                plotEnabled.remove(sensorName)
////                            }
//                        }
                    }
                }
            }

            Column(size = 9) {
                Plot(plot = vision.plot)
            }
        }
    }


public actual class ThermoSensorPlugin : VisionPlugin() {
    val plotly by require(PlotlyJsPlugin)

    actual override val tag: PluginTag get() = Companion.tag

    actual override val visionSerializersModule: SerializersModule get() = thermoVisionSerializersModule

    override fun content(target: String): Map<Name, Any> = when (target) {
        ElementVisionRenderer.TYPE -> mapOf(
            "thermoHub".asName() to thermoSensorHubRenderer,
        )

        else -> super.content(target)
    }

    public actual companion object : PluginFactory<ThermoSensorPlugin> {
        actual override val tag: PluginTag = PluginTag("controls.vision")

        actual override fun build(context: Context, meta: Meta): ThermoSensorPlugin = ThermoSensorPlugin()

    }
}