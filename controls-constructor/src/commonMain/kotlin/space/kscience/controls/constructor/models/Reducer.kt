package space.kscience.controls.constructor.models

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Degrees
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.times
import space.kscience.dataforge.context.Context

/**
 * A reducer device used for simulations only (no public properties)
 */
public class Reducer(
    context: Context,
    public val ratio: Double,
    public val input: DeviceState<Numeric<Degrees>>,
    public val output: MutableDeviceState<Numeric<Degrees>>,
) : ModelConstructor(context) {
    init {
        registerState(input)
        registerState(output)
        bindTransformedState(input, output) {
            it * ratio
        }
    }
}