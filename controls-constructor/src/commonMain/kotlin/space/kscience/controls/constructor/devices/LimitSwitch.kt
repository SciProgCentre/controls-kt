package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.registerAsProperty
import space.kscience.controls.constructor.units.Direction
import space.kscience.controls.constructor.units.NumericAmount
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
    locked: ValueState<Boolean>,
) : DeviceConstructor(context) {

    public val locked: ValueState<Boolean> = registerAsProperty(LimitSwitch.locked, locked)

    public companion object : DeviceSpec<LimitSwitch>() {
        public val locked: DevicePropertySpec<LimitSwitch, Boolean> by booleanProperty { locked.value }
    }
}

public fun <U : UnitsOfMeasurement, T : NumericAmount<U>> LimitSwitch(
    context: Context,
    limit: T,
    boundary: Direction,
    position: ValueState<T>,
): LimitSwitch = LimitSwitch(
    context,
    ValueState.map(context, position) {
        when (boundary) {
            Direction.UP -> it >= limit
            Direction.DOWN -> it <= limit
        }
    }
)