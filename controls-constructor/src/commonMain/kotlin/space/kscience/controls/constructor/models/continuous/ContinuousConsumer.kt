package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface ContinuousConsumerInterface<U : UnitsOfMeasurement, T : Amount<U>> {
    public val consumation: DeviceState<T>
    public val consumationCapacity: DeviceState<Numeric<U>>
    public val supplyRequest: LateBindDeviceState<T>

}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousConsumerInterface<U, T>.connectProducer(
    producerCapacity: DeviceState<T>,
) {
    supplyRequest.bind(producerCapacity)
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousConsumerInterface<U, T>.connectProducer(
    producerInterface: ContinuousProducerInterface<U, T>
) {
    ContinuousFlowModel.connect(producerInterface, this)
}

/**
 * Represents a model for a material discrete consumer capable of consuming material discrete based on its defined capacity
 * and requested supply. This class calculates the actual material discrete consumed and the efficiency of consumption.
 *
 * @param U The type of units of measurement for the material discrete.
 * @param context The execution context used for state management and operations.
 * @param consumationCapacity The maximum capacity for material discrete consumption of the consumer.
 * @param supplyRequest The state representing the requested material discrete to be supplied.
 *
 * @property consumation A device state representing the actual material discrete consumed,
 * calculated as the minimum of the requested supply and the consumer's capacity.
 * @property efficiency A device state representing the efficiency of the consumer, calculated
 * as the ratio of the actual consumption to the capacity.
 */
public class ContinuousConsumer<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    override val consumationCapacity: DeviceState<Numeric<U>>,
    override val supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(algebra.zero)
) : ModelConstructor(context), ContinuousConsumerInterface<U, T> {

    init {
        registerState(consumationCapacity)
        registerState(supplyRequest)
    }

    override val consumation: DeviceState<T> = combineState(
        supplyRequest,
        consumationCapacity
    ) { request, capacity ->
        with(algebra) {
            request.coerceValueIn(Numeric.zero<U>()..capacity)
        }
    }

    public val efficiency: DeviceState<Double> = combineState(
        consumation,
        consumationCapacity
    ) { consumation, capacity ->
        consumation.value / capacity.value
    }

    public companion object
}

/**
 * Creates an instance of a [ContinuousConsumer] for managing material discrete consumption based on its capacity
 * and a supply request.
 *
 * @param U The type of units of measurement for the material discrete.
 * @param context The execution context used for state management and operations.
 * @param capacity A device state representing the maximum capacity for material discrete consumption.
 * @param supplyRequest An optional late-bound device state representing the requested material discrete to be supplied.
 * Defaults to a state with an initial value of zero.
 * @return An instance of ContinuousConsumer configured with the supplied parameters.
 */
public fun <U : UnitsOfMeasurement> ContinuousConsumer(
    context: Context,
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric(0))
): ContinuousConsumer<U, Numeric<U>> = ContinuousConsumer(context, NumericAmountAlgebra<U>(), capacity, supplyRequest)

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.consumer(
    algebra: AmountAlgebra<U, T>,
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(algebra.zero),
): ContinuousConsumer<U, T> = model(ContinuousConsumer(context, algebra, capacity, supplyRequest))

public fun <U : UnitsOfMeasurement> ContinuousFlowModel.consumer(
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric(0))
): ContinuousConsumer<U, Numeric<U>> = model(ContinuousConsumer(context, capacity, supplyRequest))
