package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.units.NewtonsMeters
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.numerical
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter

//TODO use current as input

public class Drive(
    context: Context,
    force: MutableDeviceState<NumericalValue<NewtonsMeters>> = MutableDeviceState(NumericalValue(0)),
) : DeviceConstructor(context) {
    public val force: MutableDeviceState<NumericalValue<NewtonsMeters>> by property(MetaConverter.numerical(), force)
}