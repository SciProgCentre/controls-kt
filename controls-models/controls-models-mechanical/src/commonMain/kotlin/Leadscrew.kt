package space.kscience.controls.models.mechanical

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import kotlin.math.PI

/**
 * https://en.wikipedia.org/wiki/Leadscrew
 */
public class Leadscrew(
    context: Context,
    public val leverage: NumericAmount<Meters>,
) : DeviceConstructor(context) {

    public fun torqueToForce(
        stateOfTorque: ValueState<NumericAmount<NewtonsMeters>>,
    ): ValueState<NumericAmount<Newtons>> = ValueState.map(this, stateOfTorque) { torque ->
        NumericAmount(torque.value / leverage.value)
    }

    public fun degreesToMeters(
        stateOfAngle: ValueState<NumericAmount<Degrees>>,
        offset: NumericAmount<Meters> = NumericAmount(0),
    ): ValueState<NumericAmount<Meters>> = ValueState.map(this, stateOfAngle) { degrees ->
        offset + NumericAmount(degrees.value * 2 * PI / 360 * leverage.value)
    }

}