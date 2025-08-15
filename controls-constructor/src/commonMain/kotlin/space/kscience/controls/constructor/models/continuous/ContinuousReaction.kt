package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.models.continuous.ReactionRule.Companion.formula
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.AmountAlgebra
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.UnitsOfMeasurement
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

    public fun forward(input: Map<String, T>): T

    public fun backward(output: Amount<U>): Map<String, Numeric<U>>

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

            override fun forward(input: Map<String, T>): T {
                val factor = formula.minOf { (key, formulaValue) ->
                    (input[key]?.value ?: 0.0) / formulaValue.value
                }
                return with(algebra) { factor * production }
            }

            override fun backward(output: Amount<U>): Map<String, Numeric<U>> = formula.mapValues {
                Numeric(output.value * it.value.value)
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

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState( Numeric.zero())
    public val supplyRequest: Map<String, LateBindDeviceState<T>> = reaction.supplyKeys.associateWith {
        LateBindDeviceState( algebra.zero)
    }


    init {
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    // trick with casts is needed for reification to work
    private val jointSupplyRequest: DeviceState<Map<String, T>> = combineState(supplyRequest) {
        it
    }

    /**
     * A state of consumation from all sources
     */
    public val consumation: DeviceState<Map<String, T>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, T> ->
        with(algebra) {
            //compute expected amount of each supply
            val forwardRequest = reaction.forward(supplyRequest)
            //limit forward request to consumer capacity
            val forward = forwardRequest.coerceIn(algebra.zero..consumerRequest)
            //consumation from request
            val backward = reaction.backward(forward)

            //limit consumation to actually consumed
            supplyRequest.mapValues { (key, value) ->
                value.coerceValueIn(Numeric.zero<U>()..backward[key]!!)
            }
        }
    }

    /**
     * Represents a mapping of individual consumptions keyed by a string representing the associated device or identifier.
     */
    public val individualConsumation: Map<String, DeviceState<T>> = reaction.supplyKeys.associateWith { key ->
        mapState(consumation) { it[key]!! }
    }

    public val consumationCapacity: DeviceState<Map<String, Numeric<U>>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest: Numeric<U>, supplyRequest: Map<String, T> ->
        with(algebra) {
            //compute expected amount of each supply
            val forwardRequest = reaction.forward(supplyRequest)
            //limit forward request to consumer capacity
            val forward = forwardRequest.coerceIn(algebra.zero..consumerRequest)
            //consumation from request
            reaction.backward(forward)
        }
    }

    public val individualConsumationCapacity: Map<String, DeviceState<Numeric<U>>> =
        reaction.supplyKeys.associateWith { key ->
            mapState(consumationCapacity) { it[key]!! }
        }


    override val productionCapacity: DeviceState<T> = mapState(jointSupplyRequest) { supplyRequest ->
        reaction.forward(supplyRequest)
    }

    override val production: DeviceState<T> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, T> ->
        with(algebra) {
            reaction.forward(supplyRequest).coerceValueIn(Numeric.zero<U>()..consumerRequest)
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
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousReaction<U, T>.asConsumer(
    key: String
): ContinuousConsumerInterface<U, T> = supplyRequest[key]?.let { input ->
    object : ContinuousConsumerInterface<U, T> {
        override val consumation: DeviceState<T> get() = individualConsumation[key]!!
        override val consumationCapacity: DeviceState<Numeric<U>> get() = individualConsumationCapacity[key]!!
        override val supplyRequest: LateBindDeviceState<T> get() = input
    }
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