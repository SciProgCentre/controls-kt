package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.MutableDeviceState
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

public class MaterialFlowJoin<U : UnitsOfMeasurement>(
    context: Context,
    private val consumerRequest: MutableDeviceState<NumericalValue<U>>
) : ModelConstructor(context) {
}