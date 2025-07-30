package space.kscience.controls.constructor.models

import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import kotlin.math.PI

/**
 * https://en.wikipedia.org/wiki/Leadscrew
 */
public class Leadscrew(
    context: Context,
    public val leverage: Numeric<Meters>,
) : ModelConstructor(context) {

    public fun torqueToForce(
        stateOfTorque: DeviceState<Numeric<NewtonsMeters>>,
    ): DeviceState<Numeric<Newtons>> = DeviceState.map(this,stateOfTorque) { torque ->
        Numeric(torque.value / leverage.value )
    }

    public fun degreesToMeters(
        stateOfAngle: DeviceState<Numeric<Degrees>>,
        offset: Numeric<Meters> = Numeric(0),
    ): DeviceState<Numeric<Meters>> = DeviceState.map(this, stateOfAngle) { degrees ->
        offset + Numeric(degrees.value * 2 * PI / 360 * leverage.value )
    }

}