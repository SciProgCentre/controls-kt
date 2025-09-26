package center.sciprog.controls.demo.thermo

import androidx.compose.runtime.*
import app.softwork.bootstrapcompose.Badge
import app.softwork.bootstrapcompose.Card
import app.softwork.bootstrapcompose.Color
import app.softwork.bootstrapcompose.Column
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.compose.web.css.pt
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
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
import space.kscience.visionforge.html.FlexRow
import space.kscience.visionforge.onPropertyChange
import kotlin.time.Duration.Companion.milliseconds


@OptIn(DFExperimental::class)
private val converter = MetaConverter.serializable<Map<String, ThermoSensorVisionData>>()

@Composable
fun Div(
    firstClass: String,
    vararg otherClasses: String,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    content: ContentBuilder<HTMLDivElement>? = null
) {
    Div(
        attrs = {
            classes(firstClass, *otherClasses)
            attrs?.invoke(this)
        },
        content = content
    )
}

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

        var selectedSensor by remember { mutableStateOf<String?>(null) }

        sensorData.entries.sortedBy { it.key }.forEach { (sensorName, state) ->

            Card {
                Column {
                    FlexRow(
                        attrs = { classes("align-items-center") },
                    ) {
                        H3 {
                            Text(sensorName)
                        }
                        Badge(
                            backgroundColor = when (state.status) {
                                ThermoSensorStatus.NotConnected -> Color.Dark
                                ThermoSensorStatus.Normal -> Color.Secondary
                                ThermoSensorStatus.Warning -> Color.Warning
                                ThermoSensorStatus.Alarm -> Color.Danger
                            },
                            attrs = {
                                classes("ms-auto")
                                style {
                                    width(50.pt)
                                }
                                onClick {
                                    selectedSensor = if (selectedSensor == sensorName) {
                                        null
                                    } else {
                                        sensorName
                                    }
                                }
                            },
                        ) {
                            H3 {
                                Text(state.temperature.toString())
                            }

                        }
                    }
                    if (selectedSensor == sensorName) {
                        val sensorConfig = vision.sensorConfig[sensorName]

                        //FIXME could be different times on server and client
                        Div("accordion-body") {
                            P { Text("Warning threshold : ${sensorConfig?.computeWarningThreshold()}") }
                            P { Text("Alarm threshold : ${sensorConfig?.computeAlarmThreshold()}") }
                            P { Text("Averaging window : ${vision.sensorConfig[sensorName]?.averagingWindow?.milliseconds}") }
                        }
                    }
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