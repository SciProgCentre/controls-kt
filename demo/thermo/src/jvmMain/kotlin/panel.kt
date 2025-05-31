package space.kscience.controls.demo.thermo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.legend.FlowLegend
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberDoubleLinearAxisModel
import kotlinx.datetime.Instant
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.PlotNumberState
import space.kscience.controls.compose.TimeAxisModel
import space.kscience.controls.compose.asComposeState
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import java.awt.Dimension
import kotlin.time.Duration.Companion.hours

private fun temperatureToColor(temperature: Double): Color {
    val normalizedTemp: Float = (temperature.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return Color(
        red = normalizedTemp,
        green = 0f,
        blue = 1f - normalizedTemp,
        alpha = 1f
    )
}

private val maxAge = 1.hours


@OptIn(ExperimentalSplitPaneApi::class, ExperimentalKoalaPlotApi::class)
fun main() = application {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val configuration: Map<String, ThermoSensorConfig> = emptyMap()
    val modbusMaster = ModbusTCPMaster("127.0.0.1")
    val thermoHub = ModbusThermoSensorHub(context.request(DeviceManager), modbusMaster, configuration)

    val controller = ThermoHubController(thermoHub)
    controller.start()

    Window(title = "ThermoSensor dashboard", onCloseRequest = ::exitApplication) {
        window.minimumSize = Dimension(800, 400)
        MaterialTheme {

            val plotEnabled = remember {
                SnapshotStateList<String>()
            }


//            val historicData = remember {
//                SnapshotStateMap<String, SnapshotStateList<ValueWithTime<Double>>>()
//            }
//
//
//            LaunchedEffect(Unit) {
//                withContext(context.coroutineContext) {
//                    controller.sensorHub.sensors.forEach { (sensorName, analyzer) ->
//                        analyzer.sensor.usePropertyWithTime(ThermoSensor.temperature) {
//                            historicData.getOrPut(sensorName) { SnapshotStateList() }.add(it)
//                        }
//                    }
//                    //FIXME cleanup old data
//                }
//            }


            HorizontalSplitPane {
                first(400.dp) {
                    Column(modifier = Modifier.background(color = Color.LightGray).fillMaxHeight()) {
                        controller.sensorHub.sensors.forEach { (sensorName, sensor) ->

                            val temperature by sensor.temperature.asComposeState()
                            val state by sensor.status.asComposeState()

                            Row(modifier = Modifier.fillMaxWidth().background(temperatureToColor(temperature))) {
                                Text(
                                    "$sensorName: ${String.format("%.2f", temperature)}",
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = sensorName in plotEnabled,
                                    onCheckedChange = {
                                        if (it) {
                                            plotEnabled.add(sensorName)
                                        } else {
                                            plotEnabled.remove(sensorName)
                                        }
                                        plotEnabled.sort()
                                    }
                                )
                            }
                        }
                    }
                }
                second(400.dp) {
                    val palette = generateHueColorPalette(plotEnabled.size)
                    ChartLayout {
                        XYGraph<Instant, Double>(
                            xAxisModel = remember { TimeAxisModel.recent(maxAge, context.clock) },
                            yAxisModel = rememberDoubleLinearAxisModel(0.0..100.0),
                        ) {
                            plotEnabled.forEachIndexed { index, sensorName ->
                                controller.sensorHub.sensors[sensorName]?.let { sensor ->
                                    PlotNumberState(
                                        context = context,
                                        state = sensor.temperature,
                                        maxAge = maxAge,
                                        lineStyle = LineStyle(SolidColor(palette[index]))
                                    )
                                }
                            }
                        }
                        Surface {
                            FlowLegend(
                                plotEnabled.size,
                                label = {
                                    Text(plotEnabled[it], color = palette[it])
                                },
                                symbol = { i ->
                                    Symbol(
                                        modifier = Modifier.size(5.dp),
                                        fillBrush = SolidColor(palette[i])
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}