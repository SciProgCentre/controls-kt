package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.DeviceState
import space.kscience.controls.constructor.ModelConstructor
import space.kscience.controls.constructor.combineState
import space.kscience.controls.constructor.registerState
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

/**
 * Represents a model for a material flow consumer capable of consuming material flow based on its defined capacity
 * and requested supply. This class calculates the actual material flow consumed and the efficiency of consumption.
 *
 * @param U The type of units of measurement for the material flow.
 * @param context The execution context used for state management and operations.
 * @param capacity The maximum capacity for material flow consumption of the consumer.
 * @param supplyRequest The state representing the requested material flow to be supplied.
 *
 * @property consumation A device state representing the actual material flow consumed,
 * calculated as the minimum of the requested supply and the consumer's capacity.
 * @property efficiency A device state representing the efficiency of the consumer, calculated
 * as the ratio of the actual consumption to the capacity.
 */
public class MaterialFlowConsumer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: DeviceState<NumericalValue<U>>,
    public val supplyRequest: DeviceState<NumericalValue<U>>,
) : ModelConstructor(context) {

    init {
        registerState(capacity)
        registerState(supplyRequest)
    }

    public val consumation: DeviceState<NumericalValue<U>> = combineState(
        supplyRequest,
        capacity
    ) { request, capacity ->
        NumericalValue(minOf(request.value, capacity.value))
    }

    public val efficiency: DeviceState<Double> = combineState(
        consumation,
        capacity
    ) { consumation, capacity ->
        consumation.value / capacity.value
    }
}
