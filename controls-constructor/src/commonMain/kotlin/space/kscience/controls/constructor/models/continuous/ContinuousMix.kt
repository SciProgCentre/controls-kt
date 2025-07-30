package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context


public enum class JoinManagementStrategy {
    PROPORTIONAL,
    ORDERED
}


/**
 * A class responsible for managing material discrete by combining supply and consumer requests with specific
 * strategies. The resulting flows are calculated based on the defined management strategy.
 *
 * @param context The context in which the material discrete is managed.
 * @param consumerRequest The state representing the total amount requested by consumers.
 * @param supplyRequest The map of supplier identifiers to their respective supply states.
 * @param joinManagementStrategy The strategy used to manage the distribution of available supply to the consumer.
 * Defaults to the proportional strategy.
 *
 * @property consumation A state representing the consumption calculation, resulting in a distribution map
 * of available material discrete across suppliers.
 * @property production A state representing the total production as a numerical value derived from the consumation map.
 */
public class ContinuousMix<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    public val supplyKeys: Collection<String>,
    private val joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
) : ContinuousFlowModel(context), ContinuousProducerInterface<U, T> {

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric.zero())
    public val supplyRequest: Map<String, LateBindDeviceState<T>> = supplyKeys.associateWith {
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


    public val consumation: DeviceState<Map<String, T>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, T> ->

        with(algebra) {
            val totalInput: T = sum(supplyRequest.values)
            val totalOutput: Numeric<U> = consumerRequest

            when (joinManagementStrategy) {
                JoinManagementStrategy.PROPORTIONAL -> {
                    if (totalInput <= totalOutput) {
                        // Insufficient input. Give all that you have.
                        supplyRequest.mapValues { it.value }
                    } else {
                        // Sufficient input. Proportionally distributed.
                        val ratio: Double = totalOutput.value / totalInput.value
                        supplyRequest.mapValues { it.value * ratio }
                    }
                }

                JoinManagementStrategy.ORDERED -> buildMap {
                    var cumSum = zero
                    for ((key, value) in supplyRequest) {
                        val sumAfter = (cumSum + value).coerceValueIn(cumSum..totalOutput)
                        if (sumAfter.value == totalOutput.value) {
                            put(key, sumAfter - cumSum)
                            break
                        } else {
                            put(key, value)
                            cumSum = sumAfter
                        }
                    }
                }
            }

        }
    }

    /**
     * Represents a mapping of individual consumptions keyed by a string representing the associated device or identifier.
     */
    public val individualConsumation: Map<String, DeviceState<T>> =
        supplyRequest.keys.associateWith { key ->
            mapState(consumation) { it[key]!! }
        }

    override val productionCapacity: DeviceState<T> = mapState(jointSupplyRequest) { supply: Map<String, T> ->
        algebra.sum(supply.values)
    }

    override val production: DeviceState<T> = mapState(consumation) { consume ->
        algebra.sum(consume.values)
    }


    override fun toString(): String =
        "ContinuousMix(strategy=$joinManagementStrategy, consumation=${consumation.value}, production=${production.value})"
}

/**
 * Creates a continuous join model instance that is responsible for managing material discrete by combining supply
 * and consumer requests with a specific join management strategy.
 *
 * @param context The context in which the material discrete is managed.
 * @param outputNames A collection of output state identifiers representing the suppliers.
 * @param joinManagementStrategy The strategy used to distribute available supply to consumers. Defaults to
 * JoinManagementStrategy.PROPORTIONAL.
 * @return A ContinuousJoin instance configured with numeric values coupled to units of measurement.
 */
public fun <U : UnitsOfMeasurement> ContinuousMix(
    context: Context,
    outputNames: Collection<String>,
    joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL
): ContinuousMix<U, Numeric<U>> =
    ContinuousMix(context, NumericAmountAlgebra<U>(), outputNames, joinManagementStrategy)

/**
 * Creates a consumer instance for a specific supply key from a continuous mix instance.
 *
 * @param key The unique identifier of the supply for which the consumer is to be created.
 * @return A [ContinuousConsumer] instance associated with the specified key, capable of consuming material discrete
 * based on its capacity and the corresponding supply request.
 * @throws IllegalStateException If no supplier with the specified key is found in the supply requests.
 */
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.asConsumer(
    key: String
): ContinuousConsumer<U, T> = supplyRequest[key]?.let { input ->
    ContinuousConsumer(
        context = context,
        algebra = algebra,
        consumationCapacity = individualConsumation[key]!!.asNumeric(),
        supplyRequest = input
    )
} ?: error("No supplier with key $key found")


public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.connectProducer(
    key: String,
    producer: ContinuousProducerInterface<U, T>
) {
    ContinuousFlowModel.connect(producer, this.asConsumer(key))
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.connectProducer(
    key: String,
    producerCapacity: DeviceState<T>
) {
    supplyRequest[key]?.bind(producerCapacity) ?: error("No supplier with key $key found")
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix(
    producers: Map<String, ContinuousProducer<U, T>>,
    consumer: ContinuousConsumer<U, T>,
    algebra: AmountAlgebra<U, T> = consumer.algebra,
    context: Context = producers.values.first().context,
): ContinuousMix<U, T> = ContinuousMix<U, T>(
    context = context,
    algebra = algebra,
    supplyKeys = producers.keys
).also { join ->
    join.connectConsumer(consumer)
    producers.forEach { (key, producer) ->
        join.connectProducer(key, producer)
    }
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.mix(
    algebra: AmountAlgebra<U, T>,
    supplyKeys: Collection<String>,
    joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
): ContinuousMix<U, T> = model(ContinuousMix(context, algebra, supplyKeys, joinManagementStrategy))