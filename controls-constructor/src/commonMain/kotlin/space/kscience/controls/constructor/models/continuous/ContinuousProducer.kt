package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName

public interface ContinuousProducer<U : UnitsOfMatter, T : Amount<U>> {
    public val producerAlgebra: AmountAlgebra<U, T>

    public val production: ValueState<PerSecond<U, T>>
    public val productionCapacity: ValueState<PerSecond<U, T>>
    public val consumerRequest: LateBindValueState<AmountPerSecond<U>>

    public companion object
}

public interface ContinuousProducerWrapper<U : UnitsOfMatter, T : Amount<U>> : ContinuousProducer<U, T> {
    public val producer: ContinuousProducer<U, T>

    override val producerAlgebra: AmountAlgebra<U, T> get() = producer.producerAlgebra

    override val production: ValueState<PerSecond<U, T>> get() = producer.production
    override val productionCapacity: ValueState<PerSecond<U, T>> get() = producer.productionCapacity
    override val consumerRequest: LateBindValueState<AmountPerSecond<U>> get() = producer.consumerRequest
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer<U, T>.connectConsumer(
    consumerCapacity: ValueState<AmountPerSecond<U>>,
) {
    consumerRequest.bind(consumerCapacity)
}

/**
 * Connect a consumer to this [ContinuousProducer]
 */
public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer<U, T>.connectConsumer(
    consumer: ContinuousConsumer<U, T>
) {
    ContinuousFlowModel.connect(this, consumer)
}

/**
 * A model representing a producer with continuous output constrained by its capacity and consumer requests.
 *
 * @param U The type of units of measurement for the production flow.
 * @param context The execution context for state management and operations.
 * @param productionCapacity The maximum capacity state defining the upper limit of the producer's output.
 *
 * @property consumerRequest A deferred-binding state representing the material flow requested by consumers.
 * @property production A state representing the actual production flow, calculated as the minimum of the
 * consumer request and the producer's capacity.
 * @property efficiency A state representing the production efficiency, calculated as the ratio of
 * the actual production to the defined capacity.
 */
private class ContinuousProducerImpl<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val producerAlgebra: AmountAlgebra<U, T>,
    override val productionCapacity: ValueState<PerSecond<U,T>>,
    override val consumerRequest: LateBindValueState<AmountPerSecond<U>> = LateBindValueState(PerSecond.zero()),
) : DeviceConstructor(context), ContinuousProducer<U, T> {

    init {
        registerState(productionCapacity, "production.capacity".parseAsName(true))
        registerState(consumerRequest, "consumer.request".parseAsName(true))
    }

    override val production: ValueState<PerSecond<U,T>> = combineState(
        first = consumerRequest,
        second = productionCapacity,
        name = "production".asName()
    ) { request: AmountPerSecond<U>, capacity: PerSecond<U,T> ->
        with(producerAlgebra) {
            capacity.coerceValueIn(NumericAmount.zero<U>()..request)
        }
    }

    public val efficiency: ValueState<Double> = combineState(
        first = consumerRequest,
        second = productionCapacity,
        name = "efficiency".asName()
    ) { request, capacity ->
        with(producerAlgebra) {
            val production = capacity.coerceValueIn(NumericAmount.zero<U>()..request)
            production.value / capacity.value
        }
    }

}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousProducer(
    context: Context,
    producerAlgebra: AmountAlgebra<U, T>,
    productionCapacity: ValueState<PerSecond<U,T>>,
    consumerRequest: LateBindValueState<AmountPerSecond<U>> = LateBindValueState(PerSecond.zero())
): ContinuousProducer<U, T> = ContinuousProducerImpl(context, producerAlgebra, productionCapacity, consumerRequest)

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousFlowModel.producer(
    algebra: AmountAlgebra<U, T>,
    capacity: ValueState<PerSecond<U,T>>,
    modelName: Name? = null
): ContinuousProducer<U, T> = child(ContinuousProducerImpl(context, algebra, capacity), modelName)

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousFlowModel.producer(
    algebra: AmountAlgebra<U, T>,
    capacity: PerSecond<U,T>,
    modelName: Name? = null
): ContinuousProducer<U, T> = child(ContinuousProducerImpl(context, algebra, ValueState(capacity)), modelName)
