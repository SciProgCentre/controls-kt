package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface ContinuousProducerInterface<U : UnitsOfMeasurement, T : Amount<U>> {
    public val production: DeviceState<T>
    public val productionCapacity: DeviceState<T>
    public val consumerRequest: LateBindDeviceState<Numeric<U>>
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.connectConsumer(
    consumerCapacity: DeviceState<Numeric<U>>,
) {
    consumerRequest.bind(consumerCapacity)
}

/**
 * Connect a consumer to this [ContinuousProducerInterface]
 */
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousProducerInterface<U, T>.connectConsumer(
    consumer: ContinuousConsumerInterface<U, T>
) {
    ContinuousFlowModel.connect(this, consumer)
}

/**
 * A model representing a producer with continuous output constrained by its capacity and consumer requests.
 *
 * @param U The type of units of measurement for the production discrete.
 * @param context The execution context for state management and operations.
 * @param productionCapacity The maximum capacity state defining the upper limit of the producer's output.
 *
 * @property consumerRequest A deferred-binding state representing the material discrete requested by consumers.
 * @property production A state representing the actual production discrete, calculated as the minimum of the
 * consumer request and the producer's capacity.
 * @property efficiency A state representing the production efficiency, calculated as the ratio of
 * the actual production to the defined capacity.
 */
public class ContinuousProducer<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    override val productionCapacity: DeviceState<T>,
    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(context,Numeric.zero()),
) : ModelConstructor(context), ContinuousProducerInterface<U, T> {

    init {
        registerState(productionCapacity)
        registerState(consumerRequest)
    }

    override val production: DeviceState<T> = combineState(
        first = consumerRequest,
        second = productionCapacity
    ) { request: Numeric<U>, capacity: T ->
        with(algebra) {
            capacity.coerceValueIn(Numeric.zero<U>()..request)
        }
    }

    public val efficiency: DeviceState<Double> = combineState(
        consumerRequest,
        productionCapacity
    ) { request, capacity ->
        with(algebra) {
            val production = capacity.coerceValueIn(Numeric.zero<U>()..request)
            production.value / capacity.value
        }
    }

    public companion object {

        /**
         * Creates an instance of `MaterialFlowProducer` by combining the given consumer's consumption
         * state with the producer's capacity, ensuring the resulting producer respects both
         * the consumer's needs and the producer's constraints.
         *
         * @param consumer The `MaterialFlowConsumer` whose consumption requests are used to calculate
         * the producer's actual material discrete production.
         * @param capacity The capacity `DeviceState` defining the maximum discrete that this producer can handle.
         * @return A newly constructed `MaterialFlowProducer` instance with the adjusted capacity based
         * on the minimum of the provided capacity and the consumer's consumption requests.
         */
        public fun <U : UnitsOfMeasurement, T : Amount<U>> fromConsumer(
            consumer: ContinuousConsumer<U, T>,
            capacity: DeviceState<T>,
        ): ContinuousProducer<U, T> {
            return ContinuousProducer(
                context = consumer.context,
                algebra = consumer.algebra,
                productionCapacity = capacity,
            ).also { producer ->
                //provide bi-directional connection
                ContinuousFlowModel.connect(producer, consumer)
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
 * @param supplyRequest The deferred-binding state representing the material discrete supply requests.
 * Defaults to a state with an initial value of zero.
 * @return A new instance of `ContinuousProducer` configured with the provided capacity and supply requests.
 */
public fun <U : UnitsOfMeasurement> ContinuousProducer(
    context: Context,
    capacity: DeviceState<Numeric<U>>,
): ContinuousProducer<U, Numeric<U>> = ContinuousProducer(context, NumericAmountAlgebra<U>(), capacity)

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.producer(
    algebra: AmountAlgebra<U, T>,
    capacity: DeviceState<T>
): ContinuousProducer<U, T> = model(ContinuousProducer(context, algebra, capacity))

public fun <U : UnitsOfMeasurement> ContinuousFlowModel.producer(
    capacity: DeviceState<Numeric<U>>
): ContinuousProducer<U, Numeric<U>> = model(ContinuousProducer(context, capacity))