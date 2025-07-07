package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
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
public class ContinuousConsumer<T : Amount<*>>(
    context: Context,
    public val algebra: AmountAlgebra<T>,
    public val capacity: DeviceState<T>,
    public val supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(algebra.zero)
) : ModelConstructor(context) {

    init {
        registerState(capacity)
        registerState(supplyRequest)
    }

    public val consumation: DeviceState<T> = combineState(
        supplyRequest,
        capacity
    ) { request, capacity ->
        with(algebra) {
            minOf(request, capacity)
        }
    }

    public val efficiency: DeviceState<Double> = combineState(
        consumation,
        capacity
    ) { consumation, capacity ->
        consumation.value / capacity.value
    }

    public fun connectProducer(
        producerCapacity: DeviceState<T>,
    ) {
        this.supplyRequest.bind(producerCapacity)
    }

    public companion object
}

/**
 * Creates an instance of a [ContinuousConsumer] for managing material flow consumption based on its capacity
 * and a supply request.
 *
 * @param U The type of units of measurement for the material flow.
 * @param context The execution context used for state management and operations.
 * @param capacity A device state representing the maximum capacity for material flow consumption.
 * @param supplyRequest An optional late-bound device state representing the requested material flow to be supplied.
 * Defaults to a state with an initial value of zero.
 * @return An instance of ContinuousConsumer configured with the supplied parameters.
 */
public fun <U : UnitsOfMeasurement> ContinuousConsumer(
    context: Context,
    capacity: DeviceState<Numeric<U>>,
    supplyRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric(0))
): ContinuousConsumer<Numeric<U>> = ContinuousConsumer(context, NumericAmountAlgebra<U>(), capacity, supplyRequest)
