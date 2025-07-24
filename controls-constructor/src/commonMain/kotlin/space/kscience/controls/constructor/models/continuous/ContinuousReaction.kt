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

            override val supplyKeys: Collection<String> = formula.keys
            override val productKey: String = productKey

            override fun invoke(input: Map<String, T>): Map<String, T> = with(algebra) {
                //Find the lowest factor that limits production
                val factor = formula.mapValues { (key, formulaValue) ->
                    (input[key]?.value ?: 0.0) / formulaValue.value
                }.minBy { it.value }.value

                formula.mapValues { (key, formulaValue) ->
                    val input = input[key] ?: zero
                    input * (1.0 - formulaValue.value * factor / input.value)
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
    @Suppress("UNCHECKED_CAST")
    private val jointSupplyRequest: DeviceState<Map<String, T>> =
        combineState(supplyRequest) { it }


    private val reactionResult = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, T> ->
        with(algebra) {
            val reactionResult = reaction(supplyRequest)
            val production = reactionResult[reaction.productKey] ?: zero

            if (production <= consumerRequest) {
                reactionResult
            } else {
                val scale = production.value / consumerRequest.value
                reactionResult.mapValues { it.value / scale }
            }
        }
    }

    /**
     * A state of consumation from all sources
     */
    public val consumation: DeviceState<Map<String, T>> = combineState(
        jointSupplyRequest, reactionResult
    ) { supply, reaction ->
        with(algebra) {
            reaction.keys.associateWith {
                //subtract output quantity from input quantity to compute the value actually consumed
                (supply[it] ?: zero) - (reaction[it] ?: zero)
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

    override val production: DeviceState<T> = mapState(reactionResult) { result ->
        result[reaction.productKey] ?: algebra.zero
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

/**
 * Connect a consumer to this [ContinuousReaction]
 */
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousReaction<U, T>.connectConsumer(
    consumer: ContinuousConsumerInterface<U, T>
) {
    ContinuousFlowModel.connect(this, consumer)
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousReaction<U, T>.connectConsumer(
    consumerCapacity: DeviceState<Numeric<U>>
) {
    consumerRequest.bind(consumerCapacity)
}

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
