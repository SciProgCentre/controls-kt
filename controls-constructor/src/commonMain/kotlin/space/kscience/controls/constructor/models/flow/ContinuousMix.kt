package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context


public enum class JoinManagementStrategy {
    PROPORTIONAL,
    ORDERED
}


/**
 * A class responsible for managing material flow by combining supply and consumer requests with specific
 * strategies. The resulting flows are calculated based on the defined management strategy.
 *
 * @param context The context in which the material flow is managed.
 * @param consumerRequest The state representing the total amount requested by consumers.
 * @param supplyRequest The map of supplier identifiers to their respective supply states.
 * @param joinManagementStrategy The strategy used to manage the distribution of available supply to the consumer.
 * Defaults to the proportional strategy.
 *
 * @property consumation A state representing the consumption calculation, resulting in a distribution map
 * of available material flow across suppliers.
 * @property production A state representing the total production as a numerical value derived from the consumation map.
 */
public class ContinuousMix<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    public val outputNames: Collection<String>,
    private val joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
) : ModelConstructor(context), ContinuousProducerInterface<U, T> {

    override val consumerRequest: LateBindDeviceState<Numeric<U>> = LateBindDeviceState(Numeric.zero())
    public val supplyRequest: Map<String, LateBindDeviceState<T>> = outputNames.associateWith {
        LateBindDeviceState(algebra.zero)
    }


    init {
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    // trick with casts is needed for reification to work
    @Suppress("UNCHECKED_CAST")
    private val jointSupplyRequest: DeviceState<Map<String, T>> =
        DeviceState.combine(supplyRequest) { it }


    public val consumation: DeviceState<Map<String, T>> = DeviceState.combine(
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
                        if(sumAfter.value == totalOutput.value){
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

    public val partialConsumation: Map<String, DeviceState<T>> =
        supplyRequest.keys.associateWith { key ->
            consumation.map { it[key]!! }
        }

    override val productionCapacity: DeviceState<T> = DeviceState.combine(supplyRequest.values) { array ->
        algebra.sum(array)

    }

    override val production: DeviceState<T> = consumation.map { consume ->
        algebra.sum(consume.values)
    }


    override fun toString(): String =
        "ContinuousJoin(strategy=$joinManagementStrategy, consumation=${consumation.value}, production=${production.value})"
}

/**
 * Creates a continuous join model instance that is responsible for managing material flow by combining supply
 * and consumer requests with a specific join management strategy.
 *
 * @param context The context in which the material flow is managed.
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

///**
// * Converts a [ContinuousMix] instance into a [ContinuousProducer] instance.
// *
// * This transformation allows the material flow managed by the `MaterialFlowJoin` to be
// * represented as a producer model, enabling compatibility with systems or components
// * that consume material flow from producer models. The producer's production state will
// * reflect the output capacity of the join based on consumer requests.
// *
// * @return A [ContinuousProducer] representing the material flow output of the [ContinuousMix].
// */
//public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.asProducer(): ContinuousProducer<U, T> =
//    ContinuousProducer(
//        context = context,
//        algebra = algebra,
//        productionCapacity = productionCapacity,
//        consumerRequest = consumerRequest
//    )

/**
 * Converts a material flow join instance into a material flow consumer using a specified supplier key.
 *
 * @param key The identifier of the supplier whose supply request will be used to create the consumer.
 * @return A [ContinuousConsumer] instance configured with the supply request specified by the given key.
 * @throws IllegalStateException If no supplier with the given key is found in the supply requests.
 */
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.asConsumer(
    key: String
): ContinuousConsumer<U, T> = supplyRequest[key]?.let { input ->
    ContinuousConsumer(
        context = context,
        algebra = algebra,
        consumationCapacity = partialConsumation[key]!!.asNumeric(),
        supplyRequest = input
    )
} ?: error("No supplier with key $key found")


/**
 * Connect a consumer to this [ContinuousMix]
 */
public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.connectConsumer(
    consumer: ContinuousConsumerInterface<U, T>
) {
    ContinuousFlowModel.connect(this, consumer)
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousMix<U, T>.connectConsumer(
    consumerCapacity: DeviceState<Numeric<U>>
) {
    consumerRequest.bind(consumerCapacity)
}

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
    outputNames = producers.keys
).also { join ->
    join.connectConsumer(consumer)
    producers.forEach { (key, producer) ->
        join.connectProducer(key, producer)
    }
}