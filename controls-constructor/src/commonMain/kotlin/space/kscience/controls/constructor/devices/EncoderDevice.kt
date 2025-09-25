package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.units.Degrees
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.numeric
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter

/**
 * An encoder that can read an angle
 */
public class EncoderDevice(
    context: Context,
    position: DeviceState<Numeric<Degrees>>
) : DeviceConstructor(context) {
    public val position: DeviceState<Numeric<Degrees>> by property(MetaConverter.numeric(Degrees), position)
}