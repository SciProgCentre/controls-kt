package space.kscience.controls.models.mechanical

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.units.Degrees
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.numeric
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter

/**
 * An encoder that can read an angle
 */
public class EncoderDevice(
    context: Context,
    position: ValueState<NumericAmount<Degrees>>
) : DeviceConstructor(context) {
    public val position: ValueState<NumericAmount<Degrees>> by property(MetaConverter.numeric(Degrees), position)
}