package space.kscience.controls.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName

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
 * Represents a model for a material flow consumer capable of consuming material flow based on its defined capacity
 * and requested supply. This class calculates the actual material flow consumed and the efficiency of consumption.
 *
 * @param U The type of units of measurement for the material flow.
 * @param context The execution context used for state management and operations.
 * @param consumationCapacity The maximum capacity for material flow consumption of the consumer.
 * @property supplyRequest The state representing the requested material flow to be supplied.
 * @property consumation A device state representing the actual material flow consumed,
 * calculated as the minimum of the requested supply and the consumer's capacity.
 * @property efficiency A device state representing the efficiency of the consumer, calculated
 * as the ratio of the actual consumption to the capacity.
 */
public class ContinuousConsumerDevice<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U, T>,
    override val consumationCapacity: ValueState<AmountPerSecond<U>>,
) : DeviceConstructor(context), ContinuousConsumer<U, T> {

    override val supplyRequest: LateBindValueState<PerSecond<U, T>> =
        LateBindValueState(consumerAlgebra.zero.perSecond)

    override val consumation: ValueState<PerSecond<U, T>> = combineState(
        first = supplyRequest,
        second = consumationCapacity,
        name = Name.of("consumation")
    ) { request, capacity ->
        with(consumerAlgebra) {
            request.coerceValueIn(NumericAmount.zero<U>()..capacity)
        }
    }

    public val efficiency: ValueState<Double> = combineState(
        first = supplyRequest,
        second = consumationCapacity,
        name = Name.of("efficiency")
    ) { request, capacity ->
        with(consumerAlgebra) {
            val consumation = request.coerceValueIn(NumericAmount.zero<U>()..capacity)
            consumation.value / capacity.value
        }
    }


    init {
        registerState(consumationCapacity, "consumation.capacity".parseAsName(true))
        registerState(supplyRequest, "supply.request".parseAsName(true))

        registerProperty(
            name = "consumation",
            converter = MetaConverter.perSecond(consumerAlgebra.converter),
            state = consumation
        )

        registerProperty(
            name = "efficiency",
            converter = MetaConverter.double,
            state = efficiency
        )
    }
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousConsumer(
    context: Context,
    consumerAlgebra: AmountAlgebra<U, T>,
    consumationCapacity: ValueState<AmountPerSecond<U>>,
): ContinuousConsumer<U, T> = ContinuousConsumerDevice(context, consumerAlgebra, consumationCapacity)

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.consumer(
    algebra: AmountAlgebra<U, T>,
    capacity: ValueState<AmountPerSecond<U>>,
    modelName: Name? = null
): ContinuousConsumer<U, T> = child(ContinuousConsumerDevice(context, algebra, capacity), modelName)

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.consumer(
    algebra: AmountAlgebra<U, T>,
    capacity: AmountPerSecond<U>,
    modelName: Name? = null
): ContinuousConsumer<U, T> = child(ContinuousConsumerDevice(context, algebra, ValueState(capacity)), modelName)

/**
 * An interface designating a model capable of consuming material from multiple consumers.
 */
public interface ContinuousMultiConsumer<U : UnitsOfMatter, T : Amount<U>> {
    public fun asConsumer(key: String): ContinuousConsumer<U, T>
}

/**
 * Connect a producer to a single key in multiconsumer
 */
public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousMultiConsumer<U, T>.connectProducer(
    key: String,
    producer: ContinuousProducer<U, T>
) {
    ContinuousFlowModel.connect(producer, this.asConsumer(key))
}