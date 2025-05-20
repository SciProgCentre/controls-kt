package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

/**
 * Represents a model for a material flow producer capable of producing material flow up to a defined capacity.
 *
 * This class combines two device states, `capacity` and `consumerRequest`, to derive the production state,
 * which represents the actual material flow produced based on the consumer's request and the producer's capacity.
 * It also calculates the efficiency of production as the ratio of actual production to capacity.
 *
 * @param U The type of units of measurement for the material flow.
 * @param context The execution context used for state management and operations.
 * @param capacity The capacity for material flow production of the producer.
 * @param consumerRequest The state representing the requested material flow from the consumer.
 *
 * @property production A device state representing the actual material flow produced,
 * calculated as the minimum of the request and the producer's capacity.
 * @property efficiency A device state representing the efficiency of the producer, calculated
 * as the ratio of the actual production to the capacity.
 */
public class MaterialFlowProducer<U : UnitsOfMeasurement>(
    context: Context,
    private val capacity: DeviceState<NumericalValue<U>>,
    private val consumerRequest: MutableDeviceState<NumericalValue<U>>,
) : ModelConstructor(context) {

    init {
        registerState(capacity)
        registerState(consumerRequest)
    }

    public val production: DeviceState<NumericalValue<U>> = combineState(
        first = consumerRequest,
        second = capacity
    ) { request, capacity ->
        NumericalValue(minOf(request.value, capacity.value))
    }

    public val efficiency: DeviceState<Double> = combineState(
        production,
        capacity
    ) { production, capacity ->
        production.value / capacity.value
    }

    public companion object
}