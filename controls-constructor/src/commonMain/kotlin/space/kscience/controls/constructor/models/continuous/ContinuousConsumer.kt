package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface ContinuousConsumer<U : UnitsOfMatter, T : Amount<U>> {
    public val consumerAlgebra: AmountAlgebra<U, T>

    public val consumation: ValueState<PerSecond<U, T>>
    public val consumationCapacity: ValueState<AmountPerSecond<U>>
    public val supplyRequest: LateBindValueState<PerSecond<U, T>>

    public companion object
}

public interface ContinuousConsumerWrapper<U : UnitsOfMatter, T : Amount<U>> : ContinuousConsumer<U, T> {
    public val consumer: ContinuousConsumer<U, T>

    override val consumerAlgebra: AmountAlgebra<U, T> get() = consumer.consumerAlgebra


    override val consumation: ValueState<PerSecond<U, T>> get() = consumer.consumation
    override val consumationCapacity: ValueState<AmountPerSecond<U>> get() = consumer.consumationCapacity
    override val supplyRequest: LateBindValueState<PerSecond<U, T>> get() = consumer.supplyRequest
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer<U, T>.connectProducer(
    producerCapacity: ValueState<PerSecond<U, T>>,
) {
    supplyRequest.bind(producerCapacity)
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer<U, T>.connectProducer(
    producerInterface: ContinuousProducer<U, T>
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
private class ContinuousConsumerImpl<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U, T>,
    override val consumationCapacity: ValueState<AmountPerSecond<U>>,
) : ModelConstructor(context), ContinuousConsumer<U, T> {

    override val supplyRequest: LateBindValueState<PerSecond<U, T>> =
        LateBindValueState(consumerAlgebra.zero.perSecond)

    init {
        registerState(consumationCapacity)
        registerState(supplyRequest)
    }

    override val consumation: ValueState<PerSecond<U, T>> = combineState(
        supplyRequest,
        consumationCapacity
    ) { request, capacity ->
        with(consumerAlgebra) {
            request.coerceValueIn(NumericAmount.zero<U>()..capacity)
        }
    }

    public val efficiency: ValueState<Double> = combineState(
        supplyRequest,
        consumationCapacity
    ) { request, capacity ->
        with(consumerAlgebra) {
            val consumation = request.coerceValueIn(NumericAmount.zero<U>()..capacity)
            consumation.value / capacity.value
        }
    }

}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer(
    context: Context,
    consumerAlgebra: AmountAlgebra<U, T>,
    consumationCapacity: ValueState<AmountPerSecond<U>>,
): ContinuousConsumer<U, T> = ContinuousConsumerImpl(context, consumerAlgebra, consumationCapacity)

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.consumer(
    algebra: AmountAlgebra<U, T>,
    capacity: ValueState<AmountPerSecond<U>>
): ContinuousConsumer<U, T> = model(ContinuousConsumerImpl(context, algebra, capacity))

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.consumer(
    algebra: AmountAlgebra<U, T>,
    capacity: AmountPerSecond<U>
): ContinuousConsumer<U, T> = model(ContinuousConsumerImpl(context, algebra, ValueState(capacity)))