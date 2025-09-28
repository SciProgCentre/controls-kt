package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.models.continuous.ReactionRule.Companion.formula
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

/**
 * Represents a reaction rule that models the transformation of input amounts into output amounts
 * in the context of a reactive system. Each rule defines the required input substances (supplyKeys),
 * the resulting output substance (productKey), and the transformation logic (via the invoke method).
 *
 * @param U The type of units of measurement associated with the amounts.
 * @param T The type of amount, parameterized by the unit type, used to represent quantities.
 */
public interface ReactionRule<U : UnitsOfMatter, T : Amount<U>> {
    public val supplyKeys: Collection<String>

    public val productKey: String get() = DEFAULT_PRODUCT_KEY

    public fun forward(input: Map<String, PerSecond<U, T>>): PerSecond<U, T>

    public fun backward(output: Amount<U>): Map<String, AmountPerSecond<U>>

    public companion object {

        public const val DEFAULT_PRODUCT_KEY: String = "@product"

        /**
         * @param formula components needed to produce [production] of resulting substance
         */
        public fun <U : UnitsOfMatter, T: Amount<U>> formula(
            algebra: AmountAlgebra<U, T>,
            formula: Map<String, Number>,
            production: PerSecond<U, T>,
            productKey: String = DEFAULT_PRODUCT_KEY
        ): ReactionRule<U, T> = object : ReactionRule<U, T> {

            init {
                formula.forEach { (key, value) -> require(value.toDouble() > 0.0) { "Formula value for $key must be positive, but was $value" } }
            }

            override val supplyKeys: Collection<String> = formula.keys
            override val productKey: String = productKey

            override fun forward(input: Map<String, PerSecond<U, T>>): PerSecond<U, T> {
                val factor = formula.minOf { (key, formulaValue) ->
                    (input[key]?.value ?: 0.0) / formulaValue.toDouble()
                }
                return with(algebra) { production * factor}
            }

            override fun backward(output: Amount<U>): Map<String, AmountPerSecond<U>> = formula.mapValues {
                AmountPerSecond(output.value * it.value.toDouble())
            }
        }
    }
}


/**
 * Represents a continuous reaction model within a simulation context. This class models
 * the behavior of a continuous transformation process guided by reaction rules, where
 * inputs are consumed and products are generated. The reaction respects the defined
 * request and supply constraints.
 *
 * @param U The type of the units of measurement associated with the amounts.
 * @param T The type of the amount with units, representing the quantities of substances.
 * @param context The simulation context in which the reaction operates.
 * @param producerAlgebra The algebra defining the operations on the amounts of type T.
 * @param reaction The reaction rule defining consumption and production behavior
 *                 for given supply and product keys.
 */
public class ContinuousReaction<U : UnitsOfMatter, T: Amount<U>>(
    context: Context,
    override val producerAlgebra: AmountAlgebra<U, T>,
    public val reaction: ReactionRule<U, T>,
) : ModelConstructor(context), ContinuousProducerInterface<U, T> {

    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> = LateBindDeviceState(PerSecond.zero())
    public val supplyRequest: Map<String, LateBindDeviceState<PerSecond<U, T>>> = reaction.supplyKeys.associateWith {
        LateBindDeviceState(producerAlgebra.zero.perSecond)
    }


    init {
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    // trick with casts is needed for reification to work
    private val jointSupplyRequest: DeviceState<Map<String, PerSecond<U, T>>> = combineState(supplyRequest) {
        it
    }

    /**
     * A state of consumation from all sources
     */
    public val consumation: DeviceState<Map<String, PerSecond<U, T>>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, PerSecond<U, T>> ->
        with(producerAlgebra) {
            //compute expected amount of each supply
            val forwardRequest = reaction.forward(supplyRequest)
            //limit forward request to consumer capacity
            val forward = forwardRequest.coerceIn(producerAlgebra.zero..consumerRequest)
            //consumation from request
            val backward = reaction.backward(forward)

            //limit consumation to actually consumed
            supplyRequest.mapValues { (key, value) ->
                value.coerceValueIn(PerSecond.zero<U>()..(backward[key] ?: PerSecond.zero()))
            }
        }
    }

    /**
     * Represents a mapping of individual consumptions keyed by a string representing the associated device or identifier.
     */
    public val individualConsumation: Map<String, DeviceState<PerSecond<U, T>>> = reaction.supplyKeys.associateWith { key ->
        mapState(consumation) { it[key]!! }
    }

    public val consumationCapacity: DeviceState<Map<String, AmountPerSecond<U>>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest: AmountPerSecond<U>, supplyRequest: Map<String, PerSecond<U, T>> ->
        with(producerAlgebra) {
            //compute expected amount of each supply
            val forwardRequest = reaction.forward(supplyRequest)
            //limit forward request to consumer capacity
            val forward = forwardRequest.coerceIn(producerAlgebra.zero..consumerRequest)
            //consumation from request
            reaction.backward(forward)
        }
    }

    public val individualConsumationCapacity: Map<String, DeviceState<AmountPerSecond<U>>> =
        reaction.supplyKeys.associateWith { key ->
            mapState(consumationCapacity) { it[key] ?: PerSecond.zero() }
        }


    override val productionCapacity: DeviceState<PerSecond<U, T>> = mapState(jointSupplyRequest) { supplyRequest ->
        reaction.forward(supplyRequest)
    }

    override val production: DeviceState<PerSecond<U, T>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, PerSecond<U, T>> ->
        with(producerAlgebra) {
            reaction.forward(supplyRequest).coerceValueIn(PerSecond.zero<U>()..consumerRequest)
        }
    }


    override fun toString(): String =
        "ContinuousReaction(reaction=$reaction, consumation=${consumation.value}, production=${production.value})"
}

/**
 * Creates a consumer instance for a specific supply key from a continuous mix instance.
 *
 * @param key The unique identifier of the supply for which the consumer is to be created.
 * @return A [ContinuousConsumer] instance associated with the specified key, capable of consuming material discrete
 * based on its capacity and the corresponding supply request.
 * @throws IllegalStateException If no supplier with the specified key is found in the supply requests.
 */
public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousReaction<U, T>.asConsumer(
    key: String
): ContinuousConsumerInterface<U, T> = supplyRequest[key]?.let { input ->
    object : ContinuousConsumerInterface<U, T> {
        override val consumerAlgebra: AmountAlgebra<U, T> get() = this@asConsumer.producerAlgebra

        override val consumation: DeviceState<PerSecond<U, T>> get() = individualConsumation[key]!!
        override val consumationCapacity: DeviceState<AmountPerSecond<U>> get() = individualConsumationCapacity[key]!!
        override val supplyRequest: LateBindDeviceState<PerSecond<U, T>> get() = input
    }
} ?: error("No supplier with key $key found")


public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousReaction<U, T>.connectProducer(
    key: String,
    producer: ContinuousProducerInterface<U, T>
) {
    ContinuousFlowModel.connect(producer, this.asConsumer(key))
}

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousReaction<U, T>.connectProducer(
    key: String,
    producerCapacity: DeviceState<PerSecond<U, T>>
) {
    supplyRequest[key]?.bind(producerCapacity) ?: error("No supplier with key $key found")
}

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousFlowModel.reaction(
    algebra: AmountAlgebra<U, T>,
    reaction: ReactionRule<U, T>,
): ContinuousReaction<U, T> = model(ContinuousReaction(context, algebra, reaction))

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousFlowModel.reaction(
    algebra: AmountAlgebra<U, T>,
    formula: Map<String, Number>,
    production: PerSecond<U, T>,
    productKey: String = "@product"
): ContinuousReaction<U, T> = model(
    ContinuousReaction(
        context, algebra,
        reaction = formula(
            algebra = algebra,
            formula = formula,
            production = production,
            productKey = productKey
        )
    )
)