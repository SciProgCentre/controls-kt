package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface ContinuousProducerModel<T : Amount<*>> {
    public val production: DeviceState<T>
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
public class ContinuousProducer<T : Amount<*>>(
    context: Context,
    public val algebra: AmountAlgebra<T>,
    public val capacity: DeviceState<T>,
    public val consumerRequest: LateBindDeviceState<T> = LateBindDeviceState(algebra.zero),
) : ModelConstructor(context), ContinuousProducerModel<T> {

    init {
        registerState(capacity)
        registerState(consumerRequest)
    }

    override val production: DeviceState<T> = combineState(
        first = consumerRequest,
        second = capacity
    ) { request, capacity ->
        with(algebra) {
            minOf(request, capacity)
        }
    }

    public val efficiency: DeviceState<Double> = combineState(
        production,
        capacity
    ) { production, capacity ->
        production.value / capacity.value
    }

    public fun connectConsumer(
        consumerCapacity: DeviceState<T>,
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
        public fun <T : Amount<*>> fromConsumer(
            consumer: ContinuousConsumer<T>,
            capacity: DeviceState<T>,
        ): ContinuousProducer<T> {
            return ContinuousProducer(
                context = consumer.context,
                algebra = consumer.algebra,
                capacity = capacity,
            ).also { producer ->
                //provide bi-directional connection
                Connections.connect(producer, consumer)
            }
        }

    }
}

/**
 * Creates a [ContinuousProducer] instance utilizing numeric amounts with specified units,
 * constrained by capacity and responding to supply requests.
 *
 * @param context The context for managing state and performing operations for the producer.
 * @param capacity The device state representing the capacity of the producer, defining the maximum output.
 * @param supplyRequest The deferred-binding state representing the material flow supply requests.
 * Defaults to a state with an initial value of zero.
 * @return A new instance of `ContinuousProducer` configured with the provided capacity and supply requests.
 */
public fun <U : UnitsOfMeasurement> ContinuousProducer(
    context: Context,
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric(0))
): ContinuousProducer<Numeric<U>> = ContinuousProducer(context, NumericAmountAlgebra<U>(), capacity, supplyRequest)