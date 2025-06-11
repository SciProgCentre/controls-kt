package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware

public interface ContinuousProducerModel<U : UnitsOfMeasurement> : ContextAware {
    public val production: DeviceState<NumericalValue<U>>
}

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
public class ContinuousProducer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: DeviceState<NumericalValue<U>>,
    public val consumerRequest: DeviceState<NumericalValue<U>>,
) : ModelConstructor(context), ContinuousProducerModel<U> {

    init {
        registerState(capacity)
        registerState(consumerRequest)
    }

    override val production: DeviceState<NumericalValue<U>> = combineState(
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

    public companion object{

        /**
         * Creates an instance of `MaterialFlowProducer` by combining the given consumer's consumption
         * state with the producer's capacity, ensuring the resulting producer respects both
         * the consumer's needs and the producer's constraints.
         *
         * @param consumer The `MaterialFlowConsumer` whose consumption requests are used to calculate
         * the producer's actual material flow production.
         * @param capacity The capacity `DeviceState` defining the maximum flow that this producer can handle.
         * @return A newly constructed `MaterialFlowProducer` instance with the adjusted capacity based
         * on the minimum of the provided capacity and the consumer's consumption requests.
         */
        public fun  <U : UnitsOfMeasurement> fromConsumer(
            consumer: ContinuousConsumer<U>,
            capacity: DeviceState<NumericalValue<U>>,
        ): ContinuousProducer<U> {
            val minCapacity = DeviceState.combine(capacity, consumer.consumation) { capacity, consumation ->
                NumericalValue<U>(minOf(capacity.value, consumation.value))
            }
            return ContinuousProducer(
                context = consumer.context,
                capacity = minCapacity,
                consumerRequest = consumer.consumation
            )
        }

    }
}