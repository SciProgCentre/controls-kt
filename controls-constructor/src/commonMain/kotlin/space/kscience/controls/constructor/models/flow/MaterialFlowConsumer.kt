package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
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

/**
 * Creates a new instance of [MaterialFlowConsumer] based on the specified production model and capacity.
 * Adjusts the consumer's capacity to reflect the minimum value between the provided capacity and the producer's production state.
 *
 * @param producer The production model that provides the production state for material flow.
 * @param capacity The state representing the maximum capacity for material flow consumption.
 * @return A [MaterialFlowConsumer] instance configured with the adjusted capacity and production states.
 */
public fun <U: UnitsOfMeasurement> MaterialFlowConsumer(
    producer: FlowProducerModel<U>,
    capacity: DeviceState<NumericalValue<U>>,
): MaterialFlowConsumer<U> {
    val minCapacity = DeviceState.combine(capacity, producer.production) { capacity, production ->
        NumericalValue<U>(minOf(capacity.value, production.value))
    }
    return MaterialFlowConsumer(producer.context, minCapacity, producer.production)
}
