package space.kscience.controls.constructor.devices

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.models.PidParameters
import space.kscience.controls.constructor.models.PidRegulator
import space.kscience.controls.constructor.units.Meters
import space.kscience.controls.constructor.units.NewtonsMeters
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.numerical
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter

public class LinearDrive(
    drive: Drive,
    start: LimitSwitch,
    end: LimitSwitch,
    position: DeviceState<NumericalValue<Meters>>,
    pidParameters: PidParameters,
    context: Context = drive.context,
    meta: Meta = Meta.EMPTY,
) : DeviceConstructor(context, meta) {

    public val position: DeviceState<NumericalValue<Meters>> by property(MetaConverter.numerical(), position)

    public val drive: Drive by device(drive)
    public val pid: PidRegulator<Meters, NewtonsMeters>  = model(
        PidRegulator(
            context = context,
            position = position,
            output = drive.force,
            pidParameters = pidParameters
        )
    )

    public val startLimit: LimitSwitch by device(start)
    public val endLimit: LimitSwitch by device(end)
}