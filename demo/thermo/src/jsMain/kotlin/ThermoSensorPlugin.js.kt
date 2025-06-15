package center.sciprog.controls.demo.thermo

import androidx.compose.runtime.*
import app.softwork.bootstrapcompose.Alert
import app.softwork.bootstrapcompose.Color
import app.softwork.bootstrapcompose.Row
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.pt
import org.jetbrains.compose.web.dom.Text
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.startsWith
import space.kscience.plotly.PlotlyJsPlugin
import space.kscience.visionforge.VisionPlugin
import space.kscience.visionforge.html.ComposeHtmlVisionRenderer
import space.kscience.visionforge.html.ElementVisionRenderer
import space.kscience.visionforge.html.paddingAll
import space.kscience.visionforge.onPropertyChange


@OptIn(DFExperimental::class)
private val converter = MetaConverter.serializable<Map<String, ThermoSensorVisionData>>()

val thermoSensorHubRenderer =
    ComposeHtmlVisionRenderer<VisionOfThermoSensorHub> { name, vision: VisionOfThermoSensorHub, meta ->

        var sensorData: Map<String, ThermoSensorVisionData> by remember { mutableStateOf(mutableMapOf()) }

        LaunchedEffect(vision) {
            //TODO Fix upsream VisionForge listener
            vision.onPropertyChange { name, meta: Meta? ->
                if (name.startsWith("sensorData")) {
                    sensorData = vision.sensorData
                }
            }
        }

        sensorData.entries.sortedBy { it.key }.forEach { (sensorName, state) ->
            Row(
                attrs = {
                    style {
                        backgroundColor(org.jetbrains.compose.web.css.Color.lightgray)
                        paddingAll(2.pt)
                    }
                }
            ) {
                Alert(
                    when (state.status) {
                        ThermoSensorStatus.NotConnected -> Color.Dark
                        ThermoSensorStatus.Normal -> Color.Light
                        ThermoSensorStatus.Warning -> Color.Warning
                        ThermoSensorStatus.Alarm -> Color.Danger
                    }
                ) {
                    Text("$sensorName: ${state.temperature}")
                }
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