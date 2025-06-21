package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.controls.constructor.units.times
import space.kscience.dataforge.context.Context


public enum class JoinManagementStrategy {
    PROPORTIONAL,
    ORDERED
}

/**
 * A class responsible for managing material flow by combining supply and consumer requests with specific
 * strategies. The resulting flows are calculated based on the defined management strategy.
 *
 * @param U The unit of measurement applied to the numerical values handled by this class.
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
public class ContinuousJoin<U : UnitsOfMeasurement>(
    context: Context,
    public val outputNames: Collection<String>,
    private val joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
) : ModelConstructor(context), ContinuousProducerModel<U> {

    public val consumerRequest: LateBindDeviceState<NumericalValue<U>> = LateBindDeviceState(NumericalValue(0.0))
    public val supplyRequest: Map<String, LateBindDeviceState<NumericalValue<U>>> = outputNames.associateWith {
        LateBindDeviceState(NumericalValue(0.0))
    }


    init {
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    private val jointSupplyRequest: DeviceState<Map<String, NumericalValue<U>>> =
        DeviceState.combine(supplyRequest) { it }


    public val consumation: DeviceState<Map<String, NumericalValue<U>>> = DeviceState.combine(
        consumerRequest, jointSupplyRequest
    ) { consumerRequest, supplyRequest: Map<String, NumericalValue<U>> ->

        val totalInput = supplyRequest.values.sumOf { it.value }
        val totalOutput = consumerRequest.value

        when (joinManagementStrategy) {
            JoinManagementStrategy.PROPORTIONAL -> {
                if (totalInput <= totalOutput) {
                    // Insufficient input. Give all that you have.
                    supplyRequest.mapValues { it.value }
                } else {
                    // Sufficient input. Proportionally distributed.
                    val ratio = totalOutput / totalInput
                    supplyRequest.mapValues { it.value * ratio }
                }
            }

            JoinManagementStrategy.ORDERED -> buildMap {
                var cumSum = 0.0
                for ((key, value) in supplyRequest) {
                    if (cumSum + value.value > totalOutput) {
                        put(key, NumericalValue(totalOutput - cumSum))
                        break
                    } else {
                        put(key, value)
                        cumSum += value.value
                    }
                }
            }
        }
    }

    public val partialConsumation: Map<String, DeviceState<NumericalValue<U>>> =
        supplyRequest.keys.associateWith { key ->
            consumation.map { it[key]!! }
        }

    public val maximumProduction: DeviceState<NumericalValue<U>> = DeviceState.combine(supplyRequest.values) { array ->
        NumericalValue(array.sumOf { it.value })
    }

    override val production: DeviceState<NumericalValue<U>> = consumation.map { consume ->
        NumericalValue(consume.values.sumOf { it.value })
    }


    override fun toString(): String =
        "MaterialFlowJoin(strategy=$joinManagementStrategy, consumation=${consumation.value}, production=${production.value})"
}

/**
 * Converts a [ContinuousJoin] instance into a [ContinuousProducer] instance.
 *
 * This transformation allows the material flow managed by the `MaterialFlowJoin` to be
 * represented as a producer model, enabling compatibility with systems or components
 * that consume material flow from producer models. The producer's production state will
 * reflect the output capacity of the join based on consumer requests.
 *
 * @return A [ContinuousProducer] representing the material flow output of the [ContinuousJoin].
 */
public fun <U : UnitsOfMeasurement> ContinuousJoin<U>.asProducer(): ContinuousProducer<U> =
    ContinuousProducer(context, maximumProduction, consumerRequest)

/**
 * Converts a material flow join instance into a material flow consumer using a specified supplier key.
 *
 * @param key The identifier of the supplier whose supply request will be used to create the consumer.
 * @return A [ContinuousConsumer] instance configured with the supply request specified by the given key.
 * @throws IllegalStateException If no supplier with the given key is found in the supply requests.
 */
public fun <U : UnitsOfMeasurement> ContinuousJoin<U>.asConsumer(
    key: String
): ContinuousConsumer<U> = supplyRequest[key]?.let { input ->
    ContinuousConsumer(context, partialConsumation[key]!!, input)
} ?: error("No supplier with key $key found")


/**
 * Connect a consumer to this [ContinuousJoin]
 */
public fun <U : UnitsOfMeasurement> ContinuousJoin<U>.connectConsumer(
    consumer: ContinuousConsumer<U>
) {
    Connections.connect(this.asProducer(), consumer)
}

public fun <U : UnitsOfMeasurement> ContinuousJoin<U>.connectConsumer(
    consumerCapacity: DeviceState<NumericalValue<U>>
){
    consumerRequest.bind(consumerCapacity)
}

public fun <U : UnitsOfMeasurement> ContinuousJoin<U>.connectProducer(
    key: String,
    producer: ContinuousProducer<U>
) {
    Connections.connect(producer, this.asConsumer(key))
}

public fun <U : UnitsOfMeasurement> ContinuousJoin<U>.connectProducer(
    key: String,
    producerCapacity: DeviceState<NumericalValue<U>>
) {
    supplyRequest[key]?.bind(producerCapacity) ?: error("No supplier with key $key found")
}

public fun <U : UnitsOfMeasurement> ContinuousJoin(
    producers: Map<String, ContinuousProducer<U>>,
    consumer: ContinuousConsumer<U>,
    context: Context = producers.values.first().context,
): ContinuousJoin<U> = ContinuousJoin<U>(
    context = context,
    outputNames = producers.keys
).also { join ->
    join.connectConsumer(consumer)
    producers.forEach { (key, producer) ->
        join.connectProducer(key, producer)
    }
}