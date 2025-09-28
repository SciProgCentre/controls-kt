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
public class ContinuousMix<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val producerAlgebra: AmountAlgebra<U, T>,
    public val supplyKeys: Collection<String>,
    private val joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
) : ModelConstructor(context), ContinuousProducerInterface<U, T> {

    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> = LateBindDeviceState(PerSecond.zero())


    public val supplyRequest: Map<String, LateBindDeviceState<PerSecond<U, T>>> = supplyKeys.associateWith {
        LateBindDeviceState(producerAlgebra.zero.perSecond)
    }


    init {
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    // trick with casts is needed for reification to work
    @Suppress("UNCHECKED_CAST")
    private val jointSupplyRequest: DeviceState<Map<String, PerSecond<U, T>>> =
        combineState(supplyRequest) { it }


    public val consumation: DeviceState<Map<String, PerSecond<U, T>>> = combineState(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, PerSecond<U, T>> ->

        with(producerAlgebra) {
            val totalInput: PerSecond<U, T> = sum(supplyRequest.values)
            val totalOutput: AmountPerSecond<U> = consumerRequest

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
                    var cumSum = zero.perSecond
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
    public val individualConsumation: Map<String, DeviceState<PerSecond<U, T>>> =
        supplyRequest.keys.associateWith { key ->
            mapState(consumation) { it[key]!! }
        }

    override val productionCapacity: DeviceState<PerSecond<U, T>> =
        mapState(jointSupplyRequest) { supply: Map<String, PerSecond<U, T>> ->
            with(producerAlgebra) {
                sum(supply.values)
            }
        }

    override val production: DeviceState<PerSecond<U, T>> = mapState(consumation) { consume ->
        with(producerAlgebra) {
            sum(consume.values)
        }
    }


    override fun toString(): String =
        "ContinuousMix(strategy=$joinManagementStrategy, consumation=${consumation.value}, production=${production.value})"
}

/**
 * Creates a consumer instance for a specific supply key from a continuous mix instance.
 *
 * @param key The unique identifier of the supply for which the consumer is to be created.
 * @return A [ContinuousConsumer] instance associated with the specified key, capable of consuming material discrete
 * based on its capacity and the corresponding supply request.
 * @throws IllegalStateException If no supplier with the specified key is found in the supply requests.
 */
public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousMix<U, T>.asConsumer(
    key: String
): ContinuousConsumerInterface<U, T> = supplyRequest[key]?.let { input: LateBindDeviceState<PerSecond<U, T>> ->
    val consumation = individualConsumation[key]!!

    object : ContinuousConsumerInterface<U, T> {
        override val consumerAlgebra: AmountAlgebra<U, T> get() = this@asConsumer.producerAlgebra

        override val consumation: DeviceState<PerSecond<U, T>> get() = consumation

        override val consumationCapacity: DeviceState<AmountPerSecond<U>>
            get() = DeviceState.map(consumation) {
                AmountPerSecond<U>(consumation.value.value)
            }

        override val supplyRequest: LateBindDeviceState<PerSecond<U, T>> get() = input
    }
} ?: error("No supplier with key $key found")


public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousMix<U, T>.connectProducer(
    key: String,
    producer: ContinuousProducerInterface<U, T>
) {
    ContinuousFlowModel.connect(producer, asConsumer(key))
}

public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousMix<U, T>.connectProducer(
    key: String,
    producerCapacity: DeviceState<PerSecond<U, T>>
) {
    supplyRequest[key]?.bind(producerCapacity) ?: error("No supplier with key $key found")
}

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousMix(
    producers: Map<String, ContinuousProducer<U, T>>,
    consumer: ContinuousConsumer<U, T>,
    algebra: AmountAlgebra<U, T> = consumer.consumerAlgebra,
    context: Context = producers.values.first().context,
): ContinuousMix<U, T> = ContinuousMix<U, T>(
    context = context,
    producerAlgebra = algebra,
    supplyKeys = producers.keys
).also { join ->
    join.connectConsumer(consumer)
    producers.forEach { (key, producer) ->
        join.connectProducer(key, producer)
    }
}

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousFlowModel.mix(
    algebra: AmountAlgebra<U, T>,
    supplyKeys: Collection<String>,
    joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
): ContinuousMix<U, T> = model(ContinuousMix(context, algebra, supplyKeys, joinManagementStrategy))