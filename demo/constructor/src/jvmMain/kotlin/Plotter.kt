package space.kscience.controls.demo.constructor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.*
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.devices.LimitSwitch
import space.kscience.controls.constructor.devices.StepDrive
import space.kscience.controls.constructor.devices.angle
import space.kscience.controls.constructor.models.Leadscrew
import space.kscience.controls.constructor.models.coerceIn
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.asName
import java.awt.Dimension
import kotlin.random.Random


private class Plotter(
    context: Context,
    xDrive: StepDrive,
    yDrive: StepDrive,
    xStartLimit: LimitSwitch,
    xEndLimit: LimitSwitch,
    yStartLimit: LimitSwitch,
    yEndLimit: LimitSwitch,
    val paint: suspend (Color) -> Unit,
) : DeviceConstructor(context) {
    val xDrive by device(xDrive)
    val yDrive by device(yDrive)
    val xStartLimit by device(xStartLimit)
    val xEndLimit by device(xEndLimit)
    val yStartLimit by device(yStartLimit)
    val yEndLimit by device(yEndLimit)

    public fun moveToXY(x: Number, y: Number) {
        xDrive.target.value = x.toLong()
        yDrive.target.value = y.toLong()
    }

    val ticks = combineState(xDrive.position, yDrive.position) { x, y ->
        x to y
    }

    //TODO add calibration

    // TODO add draw as action
}

private suspend fun Plotter.modernArt(xRange: IntRange, yRange: IntRange) {
    while (isActive) {
        val randomX = Random.nextInt(xRange.first, xRange.last)
        val randomY = Random.nextInt(yRange.first, yRange.last)
        moveToXY(randomX, randomY)
        //TODO wait for position instead of custom delay
        delay(500)
        paint(Color(Random.nextInt()))
    }
}

private suspend fun Plotter.square(xRange: IntRange, yRange: IntRange) {
    while (isActive) {
        moveToXY(xRange.first, yRange.first)
        delay(1000)
        paint(Color.Red)

        moveToXY(xRange.first, yRange.last)
        delay(1000)
        paint(Color.Red)

        moveToXY(xRange.last, yRange.last)
        delay(1000)
        paint(Color.Red)

        moveToXY(xRange.last, yRange.first)
        delay(1000)
        paint(Color.Red)
    }
}

private val xRange = NumericAmount<Meters>(-0.5)..NumericAmount<Meters>(0.5)
private val yRange = NumericAmount<Meters>(-0.5)..NumericAmount<Meters>(0.5)
private const val ticksPerSecond = 3000.0
private val step = NumericAmount<Degrees>(1.8)


private data class PlotterPoint(
    val x: NumericAmount<Meters>,
    val y: NumericAmount<Meters>,
    val color: Color = Color.Black,
)

private class PlotterModel(
    context: Context,
    val callback: (PlotterPoint) -> Unit,
) : ModelConstructor(context) {

    private val xDrive = StepDrive(context, ticksPerSecond)
    private val xTransmission = Leadscrew(context, NumericAmount(0.01))
    val x = xTransmission.degreesToMeters(xDrive.angle(step)).coerceIn(xRange)

    private val yDrive = StepDrive(context, ticksPerSecond)
    private val yTransmission = Leadscrew(context, NumericAmount(0.01))
    val y = yTransmission.degreesToMeters(yDrive.angle(step)).coerceIn(yRange)

    val xy: ValueState<XY<Meters>> = combineState(x, y) { x, y -> XY(x, y) }

    val plotter = Plotter(
        context = context,
        xDrive = xDrive,
        yDrive = yDrive,
        xStartLimit = LimitSwitch(context, x.atStart),
        xEndLimit = LimitSwitch(context, x.atEnd),
        yStartLimit = LimitSwitch(context, x.atStart),
        yEndLimit = LimitSwitch(context, x.atEnd),
    ) { color ->
        println("Point X: ${x.value.value}, Y: ${y.value.value}, color: $color")
        callback(PlotterPoint(x.value, y.value, color))
    }
}

private val range = -1000..1000

@OptIn(ExperimentalSplitPaneApi::class)
suspend fun main() = application {
    Window(title = "Pid regulator simulator", onCloseRequest = ::exitApplication) {
        window.minimumSize = Dimension(400, 400)

        val scope = rememberCoroutineScope()

        var updateJob: Job? = remember { null }

        var points by remember { mutableStateOf<List<PlotterPoint>>(emptyList()) }

        val plotterModel = remember {
            val context = Context {
                plugin(DeviceManager)
                plugin(ClockManager)
            }

            /* Here goes the device definition block */

            PlotterModel(context) { plotterPoint ->
                points += plotterPoint
            }
        }

        /* Here goes the visualization block */

        MaterialTheme {
            HorizontalSplitPane {
                first(200.dp) {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        Button({
                            updateJob?.cancel()
                            updateJob = scope.launch {
                                plotterModel.plotter.square(range, range)
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Rectangle")
                        }
                        Button({
                            updateJob?.cancel()
                            updateJob = scope.launch {
                                plotterModel.plotter.modernArt(range, range)
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Modern Art")
                        }
                        Button({
                            updateJob?.cancel()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop")
                        }
                    }

                }
                second {
                    Controls2DCanvas(modifier = Modifier.fillMaxSize()) {
                        fun xToPx(x: NumericAmount<Meters>): Float =
                            ((x - xRange.start) / (xRange.endInclusive - xRange.start) * size.width).toFloat()

                        fun yToPx(y: NumericAmount<Meters>): Float =
                            ((y - yRange.start) / (yRange.endInclusive - yRange.start) * size.height).toFloat()


                        fun toOffset(xy: XY<Meters>): Offset = Offset(xToPx(xy.x), yToPx(xy.y))

                        observeState(plotterModel.y, "beam".asName()) { y ->
                            RectangleDrawable2D(
                                position = Offset(size.width / 2, yToPx(y)),
                                rectangleSize = Size(size.width, 10f),
                                color = Color.LightGray
                            )
                        }

                        observeState(plotterModel.xy, "head".asName()) { xy ->
                            CircleDrawable2D(
                                position = toOffset(xy),
                                radius = 10f,
                                color = Color.Black
                            )
                        }

                        snapshotFlow { points }.onEach {
                            it.forEachIndexed { index, plotterPoint ->
                                circle(
                                    "point[$index]",
                                    Offset(xToPx(plotterPoint.x), yToPx(plotterPoint.y)),
                                    radius = 5f,
                                    color = plotterPoint.color
                                )
                            }
                        }.launchIn(scope)
                    }
                }
            }
        }
    }
}