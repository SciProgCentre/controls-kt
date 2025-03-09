package space.kscience.controls.demo.constructor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import space.kscience.controls.compose.asComposeState
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.models.MaterialPoint
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import java.awt.Dimension


private class Spring(
    context: Context,
    val k: Double,
    val l0: NumericalValue<Meters>,
    val begin: DeviceState<XYZ<Meters>>,
    val end: DeviceState<XYZ<Meters>>,
) : ModelConstructor(context) {

    /**
     * Tension at the beginning point
     */
    val tension: DeviceState<XYZ<Newtons>> = combineState(begin, end) { begin: XYZ<Meters>, end: XYZ<Meters> ->
        val delta = end - begin
        val l = delta.length.value
        ((delta / l) * k * (l - l0.value)).cast(Newtons)
    }
}


private class BodyOnSprings(
    context: Context,
    mass: NumericalValue<Kilograms>,
    k: Double,
    startPosition: XYZ<Meters>,
    l0: NumericalValue<Meters> = NumericalValue(1.0),
    val xLeft: Double = -1.0,
    val xRight: Double = 1.0,
    val yBottom: Double = -1.0,
    val yTop: Double = 1.0,
) : DeviceConstructor(context) {

    val width = xRight - xLeft
    val height = yTop - yBottom

    val position = stateOf(startPosition)
    val velocity: MutableDeviceState<XYZ<MetersPerSecond>> = stateOf(XYZ(0, 0, 0))

    private val leftAnchor = stateOf(XYZ<Meters>(xLeft, (yTop + yBottom) / 2, 0.0))

    val leftSpring = model(
        Spring(context, k, l0, leftAnchor, position)
    )

    private val rightAnchor = stateOf(XYZ<Meters>(xRight, (yTop + yBottom) / 2, 0.0))

    val rightSpring = model(
        Spring(context, k, l0, rightAnchor, position)
    )

    val force: DeviceState<XYZ<Newtons>> =
        combineState(leftSpring.tension, rightSpring.tension) { left: XYZ<Newtons>, right ->
            -left - right
        }


    val body = model(
        MaterialPoint(
            context = context,
            mass = mass,
            force = force,
            position = position,
            velocity = velocity
        )
    )
}

fun main() = application {
    val initialState = XYZ<Meters>(0.05, 0.4, 0)

    Window(title = "Ball on springs", onCloseRequest = ::exitApplication) {
        window.minimumSize = Dimension(400, 400)
        MaterialTheme {
            val context = remember {
                Context("simulation")
            }

            val model = remember {
                BodyOnSprings(context, NumericalValue(10.0), 100.0, initialState)
            }

            //TODO add ability to freeze model

//            LaunchedEffect(Unit){
//                model.position.valueFlow.onEach {
//                    model.position.value = it.copy(y = model.position.value.y.coerceIn(-1.0..1.0))
//                }.collect()
//            }

            val position: XYZ<Meters> by model.body.position.asComposeState()
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun XYZ<Meters>.toOffset() = Offset(
                    ((x.value - model.xLeft) / model.width * size.width).toFloat(),
                    ((y.value - model.yBottom) / model.height * size.height).toFloat()

                )

                drawCircle(
                    Color.Red, 10f, center = position.toOffset()
                )
                drawLine(Color.Blue, model.leftSpring.begin.value.toOffset(), model.leftSpring.end.value.toOffset())
                drawLine(
                    Color.Blue,
                    model.rightSpring.begin.value.toOffset(),
                    model.rightSpring.end.value.toOffset()
                )
            }
        }
    }
}