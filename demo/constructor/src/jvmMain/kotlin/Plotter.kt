package space.kscience.controls.demo.constructor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.devices.StepDrive
import space.kscience.controls.constructor.devices.angle
import space.kscience.controls.constructor.models.Leadscrew
import space.kscience.controls.constructor.models.coerceIn
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.ClockManager
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.random.Random


private class Plotter(
    context: Context,
    xDrive: StepDrive,
    yDrive: StepDrive,
    val paint: suspend (Color) -> Unit,
) : DeviceConstructor(context) {
    val xDrive by device(xDrive)
    val yDrive by device(yDrive)

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

private val xRange = NumericalValue<Meters>(-0.5)..NumericalValue<Meters>(0.5)
private val yRange = NumericalValue<Meters>(-0.5)..NumericalValue<Meters>(0.5)
private const val ticksPerSecond = 3000.0
private val step = NumericalValue<Degrees>(1.8)


private data class PlotterPoint(
    val x: NumericalValue<Meters>,
    val y: NumericalValue<Meters>,
    val color: Color = Color.Black,
)

private class PlotterModel(
    context: Context,
    val callback: (PlotterPoint) -> Unit,
) : ModelConstructor(context) {

    private val xDrive = StepDrive(context, ticksPerSecond)
    private val xTransmission = Leadscrew(context, NumericalValue(0.01))
    val x = xTransmission.degreesToMeters(xDrive.angle(step)).coerceIn(xRange)

    private val yDrive = StepDrive(context, ticksPerSecond)
    private val yTransmission = Leadscrew(context, NumericalValue(0.01))
    val y = yTransmission.degreesToMeters(yDrive.angle(step)).coerceIn(yRange)

    val xy: DeviceState<XY<Meters>> = combineState(x, y) { x, y -> XY(x, y) }

    val plotter = Plotter(context, xDrive, yDrive) { color ->
        println("Point X: ${x.value.value}, Y: ${y.value.value}, color: $color")
        callback(PlotterPoint(x.value, y.value, color))
    }
}

suspend fun main() = application {
    Window(title = "Pid regulator simulator", onCloseRequest = ::exitApplication) {
        window.minimumSize = Dimension(400, 400)

        val points = remember { mutableStateListOf<PlotterPoint>() }
        var position by remember { mutableStateOf(XY<Meters>(0, 0)) }

        LaunchedEffect(Unit) {
            val context = Context {
                plugin(DeviceManager)
                plugin(ClockManager)
            }

            /* Here goes the device definition block */

            val plotterModel = PlotterModel(context) { plotterPoint ->
                points.add(plotterPoint)
            }

            /* Start visualization program */

            plotterModel.xy.valueFlow.onEach {
                position = it
            }.launchIn(this)

            /* run program */


            val range = -1000..1000
//            plotterModel.plotter.modernArt(range, range)
            plotterModel.plotter.square(range, range)

        }


        /* Here goes the visualization block */

        MaterialTheme {
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun toOffset(x: NumericalValue<Meters>, y: NumericalValue<Meters>): Offset {
                    val canvasX = (x - xRange.start) / (xRange.endInclusive - xRange.start) * size.width
                    val canvasY = (y - yRange.start) / (yRange.endInclusive - yRange.start) * size.height
                    return Offset(canvasX.toFloat(), canvasY.toFloat())
                }

                val center = toOffset(position.x, position.y)


                drawRect(
                    Color.LightGray,
                    topLeft = Offset(0f, center.y - 5f),
                    size = Size(size.width, 10f)
                )

                drawCircle(Color.Black, radius = 10f, center = center)


                points.forEach {
                    drawCircle(it.color, radius = 2f, center = toOffset(it.x, it.y))
                }
            }
        }
    }

}