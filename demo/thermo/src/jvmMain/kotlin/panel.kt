package center.sciprog.controls.demo.thermo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.legend.FlowLegend
import io.github.koalaplot.core.legend.LegendLocation
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberDoubleLinearAxisModel
import kotlinx.datetime.*
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
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
import kotlin.time.Duration.Companion.minutes

private fun temperatureToColor(temperature: Double): Color {
    val normalizedTemp: Float = (temperature.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return Color(
        red = normalizedTemp,
        green = 0f,
        blue = 1f - normalizedTemp,
        alpha = 1f
    )
}

private val maxAge = 10.minutes

private val timeFormat = LocalDateTime.Format {
    hour(padding = Padding.ZERO)
    char(':')
    minute(padding = Padding.ZERO)
    char(':')
    second(padding = Padding.ZERO)
}

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalKoalaPlotApi::class)
@Composable
private fun MainScreen(hub: ThermoSensorHub) {

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
            Column(
                modifier = Modifier.background(color = Color.LightGray)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                hub.sensors.forEach { (sensorName, sensor) ->

                    val temperature by sensor.temperature.asComposeState()
                    val state by sensor.status.asComposeState()

                    Surface(
                        elevation = 5.dp,
                        modifier = Modifier.fillMaxWidth().padding(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp).background(
                                when (state) {
                                    ThermoSensorStatus.NotConnected -> Color.Gray
                                    ThermoSensorStatus.Normal -> Color.White
                                    ThermoSensorStatus.Warning -> Color.Yellow
                                    ThermoSensorStatus.Alarm -> Color.Red
                                }
                            )
                        ) {
                            Text(sensorName, fontWeight = FontWeight.Bold)

                            Text(
                                String.format("%.2f", temperature),
                                color = temperatureToColor(temperature),
                                modifier = Modifier.weight(1f).padding(horizontal = 5.dp)
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
        }
        second(400.dp) {
            val palette = generateHueColorPalette(plotEnabled.size)
            ChartLayout(
                legend = {
                    if (plotEnabled.isNotEmpty()) {
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
                },
                legendLocation = LegendLocation.BOTTOM
            ) {
                XYGraph<Instant, Double>(
                    xAxisModel = remember { TimeAxisModel.recent(maxAge, hub.context.clock, 100.dp) },
                    yAxisModel = rememberDoubleLinearAxisModel(-10.0..110.0, minimumMajorTickIncrement = 10.0),
                    xAxisTitle = "Time",
                    xAxisLabels = { time -> time.toLocalDateTime(TimeZone.currentSystemDefault()).format(timeFormat) },
                    yAxisLabels = { value -> String.format("%.2f", value)}
                ) {
                    plotEnabled.forEachIndexed { index, sensorName ->
                        hub.sensors[sensorName]?.let { sensor ->
                            PlotNumberState(
                                context = hub.context,
                                state = sensor.temperature,
                                maxAge = maxAge,
                                lineStyle = LineStyle(SolidColor(palette[index]))
                            )
                        }
                    }
                }
            }
        }
    }
}


fun main() = application {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val configuration: Map<String, ThermoSensorConfig> = generateTestConfig(
        numberOfUnits = 1
    )
    context.launchModbusSimulator(configuration)
    Thread.sleep(200)

    val modbusMaster = ModbusTCPMaster("127.0.0.1", 9090)
    modbusMaster.connect()

    val thermoHub = ModbusThermoSensorHub(context.request(DeviceManager), modbusMaster, configuration)

    thermoHub.serveOpc(context)


    Window(title = "ThermoSensor dashboard", onCloseRequest = {
        modbusMaster.disconnect()
        context.close()
        exitApplication()
    }) {
        window.minimumSize = Dimension(800, 400)
        MaterialTheme {
            MainScreen(thermoHub)
        }
    }
}