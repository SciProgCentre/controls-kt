package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.legend.FlowLegend
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.toString
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberDoubleLinearAxisModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Instant
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.compose.NumberTextField
import space.kscience.controls.compose.PlotNumericState
import space.kscience.controls.compose.TimeAxisModel
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.devices.Drive
import space.kscience.controls.constructor.devices.LimitSwitch
import space.kscience.controls.constructor.devices.LinearDrive
import space.kscience.controls.constructor.models.Inertia
import space.kscience.controls.constructor.models.Leadscrew
import space.kscience.controls.constructor.models.MutableRangeState
import space.kscience.controls.constructor.models.PidParameters
import space.kscience.controls.constructor.onTimer
import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.constructor.units.Meters
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.hubMessageFlow
import space.kscience.controls.manager.install
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import java.awt.Dimension
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


class Modulator(
    context: Context,
    target: MutableDeviceState<Numeric<Meters>>,
    var timeStep: Duration = 5.milliseconds,
    var freq: Double = 0.1,
) : DeviceConstructor(context) {
    private val clockStart = clock.now()

    private val modulation = onTimer(timeStep) { _, next ->
        val timeFromStart = next - clockStart
        val t = timeFromStart.toDouble(DurationUnit.SECONDS)
        target.value = Numeric(
            5 * sin(2.0 * PI * freq * t) +
                    sin(2 * PI * 21 * freq * t + 0.02 * (timeFromStart / timeStep))
        )
    }
}


private val mass = Numeric<Kilograms>(1)

private val leverage = Numeric<Meters>(1.0)

private val maxAge = 10.seconds

private val range = -6.0..6.0

/**
 * The whole physical model is here
 */
internal fun createLinearDriveModel(
    context: Context,
    pidParameters: PidParameters,
    mass: Numeric<Kilograms>,
    leverage: Numeric<Meters>,
    position: MutableRangeState<Numeric<Meters>>,
): LinearDrive {

    //create a drive model with zero starting force
    val drive = Drive(context)

    //a screw drive to convert a rotational moment into a force
    val leadscrew = Leadscrew(context, leverage)


    /**
     * Create an inertia model.
     * The inertia uses drive force as input. Position is used as both input and output
     *
     * Force is the input parameter, position is output parameter
     *
     */
    val inertiaModel = Inertia.linear(
        context = context,
        force = leadscrew.torqueToForce(drive.force),
        mass = mass,
        position = position
    )

    /**
     * Create a limit switches from physical position
     */
    val startLimitSwitch = LimitSwitch(context, position.atStart)
    val endLimitSwitch = LimitSwitch(context, position.atEnd)

    /**
     * Install the resulting device
     */
    return LinearDrive(drive, startLimitSwitch, endLimitSwitch, position, pidParameters)

}

private fun createModulator(linearDrive: LinearDrive): Modulator = linearDrive.context.install(
    "modulator",
    Modulator(linearDrive.context, linearDrive.pid.target)
)

private val startPid = PidParameters(kp = 250.0, ki = 0.0, kd = -20.0, timeStep = 20.milliseconds)

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalKoalaPlotApi::class)
fun main() = application {
    val context = remember {
        Context {
            plugin(DeviceManager)
            plugin(ClockManager)
        }
    }

    var pidParameters by remember {
        mutableStateOf(startPid)
    }

    val linearDrive: LinearDrive = remember {
        context.install(
            "linearDrive",
            createLinearDriveModel(
                context = context,
                pidParameters = pidParameters,
                mass = mass,
                leverage = leverage,
                // Create a physical position coerced in a given range
                position = MutableRangeState<Meters>(0.0, range)
            )
        )
    }

    val modulator = remember {
        context.install("modulator", createModulator(linearDrive))
    }

    //bind pid parameters
    LaunchedEffect(Unit) {

        // start listening to local device hub
        context.request(DeviceManager).hubMessageFlow()
            .filterIsInstance<PropertyChangedMessage>() // filter only property change messages
            //.filter { it.sourceDevice == "linearDrive".asName()} //optionally filter by device name
            .onEach {
                println("${it.sourceDevice} >> ${it.property} changed to ${it.value}")
            }.launchIn(this)

        snapshotFlow {
            pidParameters
        }.onEach {
            linearDrive.pid.pidParameters = pidParameters
        }.collect()
    }

    Window(title = "Pid regulator simulator", onCloseRequest = ::exitApplication) {
        window.minimumSize = Dimension(800, 400)
        MaterialTheme {
            HorizontalSplitPane {
                first(400.dp) {
                    Column(modifier = Modifier.background(color = Color.LightGray).fillMaxHeight()) {
                        Row {
                            Text("kp:", Modifier.align(Alignment.CenterVertically).width(50.dp).padding(5.dp))
                            NumberTextField(
                                value = pidParameters.kp,
                                onValueChange = { pidParameters = pidParameters.copy(kp = it.toDouble()) },
                                formatter = { String.format("%.3f", it.toDouble()) },
                                step = 0.01,
                                modifier = Modifier.width(200.dp),
                            )
                            Slider(
                                pidParameters.kp.toFloat(),
                                { pidParameters = pidParameters.copy(kp = it.toDouble()) },
                                valueRange = 0f..100f,
                                steps = 100
                            )
                        }
                        Row {
                            Text("ki:", Modifier.align(Alignment.CenterVertically).width(50.dp).padding(5.dp))
                            NumberTextField(
                                value = pidParameters.ki,
                                onValueChange = { pidParameters = pidParameters.copy(ki = it.toDouble()) },
                                formatter = { String.format("%.3f", it.toDouble()) },
                                step = 0.01,
                                modifier = Modifier.width(200.dp),
                            )

                            Slider(
                                pidParameters.ki.toFloat(),
                                { pidParameters = pidParameters.copy(ki = it.toDouble()) },
                                valueRange = -10f..10f,
                                steps = 100
                            )
                        }
                        Row {
                            Text("kd:", Modifier.align(Alignment.CenterVertically).width(50.dp).padding(5.dp))
                            NumberTextField(
                                value = pidParameters.kd,
                                onValueChange = { pidParameters = pidParameters.copy(kd = it.toDouble()) },
                                formatter = { String.format("%.3f", it.toDouble()) },
                                step = 0.01,
                                modifier = Modifier.width(200.dp),
                            )

                            Slider(
                                pidParameters.kd.toFloat(),
                                { pidParameters = pidParameters.copy(kd = it.toDouble()) },
                                valueRange = -10f..10f,
                                steps = 100
                            )
                        }

                        Row {
                            Text("dt:", Modifier.align(Alignment.CenterVertically).width(50.dp).padding(5.dp))
                            TextField(
                                pidParameters.timeStep.toString(DurationUnit.MILLISECONDS),
                                { pidParameters = pidParameters.copy(timeStep = it.toDouble().milliseconds) },
                                Modifier.width(200.dp),
                                enabled = false
                            )

                            Slider(
                                pidParameters.timeStep.toDouble(DurationUnit.MILLISECONDS).toFloat(),
                                { pidParameters = pidParameters.copy(timeStep = it.toDouble().milliseconds) },
                                valueRange = 1f..100f,
                                steps = 100
                            )
                        }
                        Row {
                            Button(onClick = {
                                pidParameters = startPid
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Reset")
                            }
                        }
                    }
                }
                second(400.dp) {
                    ChartLayout {
                        XYGraph<Instant, Double>(
                            xAxisModel = remember { TimeAxisModel.recent(maxAge, context.clock) },
                            yAxisModel = rememberDoubleLinearAxisModel((range.start - 1.0)..(range.endInclusive + 1.0)),
                            xAxisTitle = { Text("Time in seconds relative to current") },
                            xAxisLabels = { it: Instant ->
                                Text(
                                    (context.clock.now() - it).toDouble(
                                        DurationUnit.SECONDS
                                    ).toString(2)
                                )
                            },
                            yAxisLabels = { it: Double -> Text(it.toString(2)) }
                        ) {
                            PlotNumericState(
                                context = context,
                                state = linearDrive.position,
                                maxAge = maxAge,
                                sampling = 50.milliseconds,
                                lineStyle = LineStyle(SolidColor(Color.Blue))
                            )
                            PlotNumericState(
                                context = context,
                                state = linearDrive.pid.target,
                                maxAge = maxAge,
                                sampling = 50.milliseconds,
                                lineStyle = LineStyle(SolidColor(Color.Red))
                            )
                        }
                        Surface {
                            FlowLegend(3, label = {
                                when (it) {
                                    0 -> {
                                        Text("Body position", color = Color.Blue)
                                    }

                                    1 -> {
                                        Text("Regulator target", color = Color.Red)
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}