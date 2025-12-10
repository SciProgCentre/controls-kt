package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.property
import space.kscience.controls.constructor.units.NewtonsMeters
import space.kscience.controls.constructor.units.NumericAmount
import space.kscience.controls.constructor.units.numeric
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter

//TODO use current as input

public class Drive(
    context: Context,
    force: MutableValueState<NumericAmount<NewtonsMeters>> = MutableDeviceState(NumericAmount(0)),
) : DeviceConstructor(context) {
    public val force: MutableValueState<NumericAmount<NewtonsMeters>> by property(MetaConverter.numeric(NewtonsMeters), force)
}