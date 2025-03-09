package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.units.Degrees
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.numerical
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter

/**
 * An encoder that can read an angle
 */
public class EncoderDevice(
    context: Context,
    position: DeviceState<NumericalValue<Degrees>>
) : DeviceConstructor(context) {
    public val position: DeviceState<NumericalValue<Degrees>> by property(MetaConverter.numerical<Degrees>(), position)
}