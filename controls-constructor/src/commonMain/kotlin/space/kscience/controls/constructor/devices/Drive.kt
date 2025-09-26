package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.units.NewtonsMeters
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.numeric
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter

//TODO use current as input

public class Drive(
    context: Context,
    force: MutableDeviceState<Numeric<NewtonsMeters>> = MutableDeviceState(Numeric(0)),
) : DeviceConstructor(context) {
    public val force: MutableDeviceState<Numeric<NewtonsMeters>> by property(MetaConverter.numeric(NewtonsMeters), force)
}