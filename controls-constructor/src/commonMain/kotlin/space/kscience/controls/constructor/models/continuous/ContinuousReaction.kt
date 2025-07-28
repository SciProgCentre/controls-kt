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
public interface ReactionRule<U : UnitsOfMeasurement, T : Amount<U>> {
    public val supplyKeys: Collection<String>

    public val productKey: String

    public operator fun invoke(input: Map<String, T>): Map<String, T>

    public companion object {


        /**
         * @param formula components needed to produce [production] of resulting substance
         */
        public fun <U : UnitsOfMeasurement, T : Amount<U>> formula(
            algebra: AmountAlgebra<U, T>,
            formula: Map<String, Numeric<U>>,
            production: T = algebra.one,
            productKey: String = "@product"
        ): ReactionRule<U, T> = object : ReactionRule<U, T> {

            init {
                formula.forEach { (key, value) -> require(value.value > 0.0) { "Formula value for $key must be positive, but was $value" } }
            }

            override val supplyKeys: Collection<String> = formula.keys
            override val productKey: String = productKey

            override fun invoke(input: Map<String, T>): Map<String, T> = with(algebra) {
                //Find the lowest factor that limits production
                val factor = formula.mapValues { (key, formulaValue) ->
                    (input[key]?.value ?: 0.0) / formulaValue.value
                }.minBy { it.value }.value

                formula.mapValues { (key, formulaValue) ->
                    val input = input[key] ?: zero
                    if (input.value == 0.0) {
                        zero
                    } else {
                        input * (1.0 - formulaValue.value * factor / input.value)
                    }
                } + (productKey to production * factor)
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
 * @param algebra The algebra defining the operations on the amounts of type T.
 * @param reaction The reaction rule defining consumption and production behavior
 *                 for given supply and product keys.
 */
public class ContinuousReaction<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    public val reaction: ReactionRule<U, T>,
) : ModelConstructor(context), ContinuousProducerInterface<U, T> {

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric.zero())
    public val supplyRequest: Map<String, LateBindDeviceState<T>> = reaction.supplyKeys.associateWith {
        LateBindDeviceState(algebra.zero)
    }


    init {
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    // trick with casts is needed for reification to work
    private val jointSupplyRequest: DeviceState<Map<String, T>> = combineState(supplyRequest) {
        it
    }


    private val reactionBalance: DeviceState<Map<String, Pair<T, T>>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, T> ->
        with(algebra) {
            val reactionResult = reaction(supplyRequest)
            val production = reactionResult[reaction.productKey] ?: zero

            check(reactionResult.all { (key, value) -> key == reaction.productKey || value <= supplyRequest[key]!! }) {
                "reaction remain exceeds supply request: $reactionResult, $supplyRequest"
            }

            if (production <= consumerRequest) {
                reactionResult.mapValues { (key, value) -> (supplyRequest[key] ?: zero) to value }
            } else {
                val scale = production.value / consumerRequest.value
                check(scale > 0.0) { "production ratio must be positive" }
                reactionResult.mapValues { (key, value) -> (supplyRequest[key] ?: zero) to (value / scale) }
            }
        }
    }

    /**
     * A state of consumation from all sources
     */
    public val consumation: DeviceState<Map<String, T>> = mapState(
        reactionBalance
    ) { balance ->
        with(algebra) {
            balance.mapValues { (key, balance) ->
                //subtract output quantity from input quantity to compute the value actually consumed
                val res = balance.first - balance.second
                check(key == reaction.productKey ||res.value >= 0.0) {
                    "Reaction balance for key $key is negative: $balance"
                }
                res
            }
        }
    }

    /**
     * Represents a mapping of individual consumptions keyed by a string representing the associated device or identifier.
     */
    public val individualConsumation: Map<String, DeviceState<T>> =
        supplyRequest.keys.associateWith { key ->
            consumation.map { it[key]!! }
        }

    override val productionCapacity: DeviceState<T> = mapState(jointSupplyRequest) { supplyRequest ->
        val reactionResult = reaction(supplyRequest)
        reactionResult[reaction.productKey] ?: algebra.zero
    }

    override val production: DeviceState<T> = mapState(reactionBalance) { result ->
        result[reaction.productKey]?.second ?: algebra.zero
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
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousReaction<U, T>.asConsumer(
    key: String
): ContinuousConsumer<U, T> = supplyRequest[key]?.let { input ->
    ContinuousConsumer(
        context = context,
        algebra = algebra,
        consumationCapacity = individualConsumation[key]!!.asNumeric(),
        supplyRequest = input
    )
} ?: error("No supplier with key $key found")


public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousReaction<U, T>.connectProducer(
    key: String,
    producer: ContinuousProducerInterface<U, T>
) {
    ContinuousFlowModel.connect(producer, this.asConsumer(key))
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousReaction<U, T>.connectProducer(
    key: String,
    producerCapacity: DeviceState<T>
) {
    supplyRequest[key]?.bind(producerCapacity) ?: error("No supplier with key $key found")
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.reaction(
    algebra: AmountAlgebra<U, T>,
    reaction: ReactionRule<U, T>,
): ContinuousReaction<U, T> = model(ContinuousReaction(context, algebra, reaction))

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.reaction(
    algebra: AmountAlgebra<U, T>,
    formula: Map<String, Numeric<U>>,
    production: T = algebra.one,
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