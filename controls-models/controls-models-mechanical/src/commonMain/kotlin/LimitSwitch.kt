package space.kscience.controls.models.mechanical

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.registerProperty
import space.kscience.controls.constructor.units.Direction
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.spec.AbstractDeviceSpec
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter


/**
 * A device that detects if a motor hits the end of its range
 */
public class LimitSwitch(
    context: Context,
    locked: ValueState<Boolean>,
) : DeviceConstructor(context) {

    public val locked: ValueState<Boolean> = registerProperty(LimitSwitch.locked, locked)

    public companion object : AbstractDeviceSpec() {
        public val locked: DevicePropertySpec<Boolean> by property(MetaConverter.boolean)
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