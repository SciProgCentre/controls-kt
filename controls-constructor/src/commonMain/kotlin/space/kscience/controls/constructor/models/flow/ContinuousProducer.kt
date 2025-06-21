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
 * A model representing a producer with continuous output constrained by its capacity and consumer requests.
 *
 * @param U The type of units of measurement for the production flow.
 * @param context The execution context for state management and operations.
 * @param capacity The maximum capacity state defining the upper limit of the producer's output.
 *
 * @property consumerRequest A deferred-binding state representing the material flow requested by consumers.
 * @property production A state representing the actual production flow, calculated as the minimum of the
 * consumer request and the producer's capacity.
 * @property efficiency A state representing the production efficiency, calculated as the ratio of
 * the actual production to the defined capacity.
 */
public class ContinuousProducer<U : UnitsOfMeasurement>(
    context: Context,
    public val capacity: DeviceState<NumericalValue<U>>,
    public val consumerRequest: LateBindDeviceState<NumericalValue<U>> = LateBindDeviceState(NumericalValue(0.0)),
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

    public fun connectConsumer(
        consumerCapacity: DeviceState<NumericalValue<U>>,
    ) {
        this.consumerRequest.bind(consumerCapacity)
    }

    public companion object {

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
        public fun <U : UnitsOfMeasurement> fromConsumer(
            consumer: ContinuousConsumer<U>,
            capacity: DeviceState<NumericalValue<U>>,
        ): ContinuousProducer<U> {
//            val minCapacity = DeviceState.combine(capacity, consumer.consumation) { capacity, consumation ->
//                NumericalValue<U>(minOf(capacity.value, consumation.value))
//            }
            return ContinuousProducer(
                context = consumer.context,
                capacity = capacity,
            ).also { producer ->
                //provide bi-directional connection
                Connections.connect(producer,consumer)
            }
        }

    }
}