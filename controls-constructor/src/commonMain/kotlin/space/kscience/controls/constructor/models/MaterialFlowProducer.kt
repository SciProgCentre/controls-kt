package space.kscience.controls.constructor.models

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

/**
 *
 * @param outputFlow a production flow of [U] per second
 */
public class MaterialFlowProducer<U : UnitsOfMeasurement>(
    context: Context,
    public val outputFlow: MutableDeviceState<NumericalValue<U>>,
    nominalOutput: NumericalValue<U>,
) : ModelConstructor(context) {
    init {
        registerState(outputFlow)
    }

    public val maximumOutput: MutableDeviceState<NumericalValue<U>> = stateOf(
        nominalOutput
    )

    public val efficiency: DeviceState<Double> = combineState(
        outputFlow,
        maximumOutput
    ) { output, maximum ->
        output.value / maximum.value
    }
}

public class MaterialFlowConsumer<U : UnitsOfMeasurement>(
    context: Context,
    public val inputFlow: DeviceState<NumericalValue<U>>,
) : ModelConstructor(context) {
    init {
        registerState(inputFlow)
    }
}