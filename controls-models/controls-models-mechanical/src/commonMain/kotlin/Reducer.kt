package space.kscience.controls.models.mechanical

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Degrees
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.times
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name

/**
 * A reducer device used for simulations only (no public properties)
 */
public class Reducer(
    context: Context,
    public val ratio: Double,
    public val input: ValueState<NumericAmount<Degrees>>,
    public val output: MutableValueState<NumericAmount<Degrees>>,
) : DeviceConstructor(context) {
    init {
        registerState(input, Name.of("input"))
        registerState(output, Name.of("output"))
        bindTransformedState(input, output) {
            it * ratio
        }
    }
}