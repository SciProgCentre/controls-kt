package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.registerAsProperty
import space.kscience.controls.constructor.units.Direction
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.DeviceSpec
import space.kscience.controls.spec.booleanProperty
import space.kscience.dataforge.context.Context


/**
 * A device that detects if a motor hits the end of its range
 */
public class LimitSwitch(
    context: Context,
    locked: DeviceState<Boolean>,
) : DeviceConstructor(context) {

    public val locked: DeviceState<Boolean> = registerAsProperty(LimitSwitch.locked, locked)

    public companion object : DeviceSpec<LimitSwitch>() {
        public val locked: DevicePropertySpec<LimitSwitch, Boolean> by booleanProperty { locked.value }
    }
}

public fun <U : UnitsOfMeasurement, T : Numeric<U>> LimitSwitch(
    context: Context,
    limit: T,
    boundary: Direction,
    position: DeviceState<T>,
): LimitSwitch = LimitSwitch(
    context,
    DeviceState.map(position) {
        when (boundary) {
            Direction.UP -> it >= limit
            Direction.DOWN -> it <= limit
        }
    }
)