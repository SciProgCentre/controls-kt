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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.asComposeState
import space.kscience.controls.compose.letsplot.PlotNumberState
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
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

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun MainScreen(hub: ThermoSensorHub, config: ThermoSensorHubConfig) {

    val plotEnabled = remember {
        SnapshotStateList<String>().also { list ->
            config.sensors.forEach {
                if (it.value.showPlot) {
                    list.add(it.key)
                }
            }
        }
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
            TimeSeriesPlot(
                modifier = Modifier.fillMaxSize(),
                xAxisTitle = "Time",
                yAxisTitle = "Temperature"
            ) {
                plotEnabled.forEach { sensorName ->
                    hub.sensors[sensorName]?.let { sensor ->
                        PlotNumberState(
                            context = hub.context,
                            state = sensor.temperature,
                            name = sensorName,
                            maxAge = maxAge,
                        )
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

    val config = generateTestConfig(4, 4)

    context.launchModbusSimulator(config)

    val thermoHub = context.ThermoSensorHub(config)

    Window(title = "ThermoSensor dashboard", onCloseRequest = {
        thermoHub.context.close()
        exitApplication()
    }) {
        window.minimumSize = Dimension(800, 400)
        MaterialTheme {
            MainScreen(thermoHub, config)
        }
    }
}