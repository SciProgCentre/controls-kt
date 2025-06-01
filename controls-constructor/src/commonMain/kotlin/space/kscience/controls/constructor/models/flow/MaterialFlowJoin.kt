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
 * @param outputCapacity The state representing the maximum output capacity.
 * @param consumerRequest The state representing the total amount requested by consumers.
 * @param supplyRequest The map of supplier identifiers to their respective supply states.
 * @param joinManagementStrategy The strategy used to manage the distribution of available supply to the consumer.
 * Defaults to the proportional strategy.
 *
 * @property consumation A state representing the consumption calculation, resulting in a distribution map
 * of available material flow across suppliers.
 * @property production A state representing the total production as a numerical value derived from the consumation map.
 * @property producer A producer instance responsible for handling material flow production based on internal
 * calculations and state dependencies.
 * @property consumers A map of consumer instances, each keyed by its identifier, representing individual
 * suppliers participating in the material flow system.
 */
public class MaterialFlowJoin<U : UnitsOfMeasurement>(
    context: Context,
    public val outputCapacity: DeviceState<NumericalValue<U>>,
    public val consumerRequest: DeviceState<NumericalValue<U>>,
    public val supplyRequest: Map<String, DeviceState<NumericalValue<U>>>,
    private val joinManagementStrategy: JoinManagementStrategy = JoinManagementStrategy.PROPORTIONAL,
) : ModelConstructor(context) {

    init {
        registerState(outputCapacity)
        registerState(consumerRequest)
        supplyRequest.values.forEach(::registerState)
    }

    private val supplyRequestState = DeviceState.combine(supplyRequest) { it }

    public val consumation: DeviceState<Map<String, NumericalValue<U>>> = DeviceState.combine(
        outputCapacity, consumerRequest, supplyRequestState
    ) { outputCapacity, consumerRequest, supplyRequest: Map<String, NumericalValue<U>> ->

        val totalInput = supplyRequest.values.sumOf { it.value }
        val totalOutput = minOf(consumerRequest.value, outputCapacity.value)

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

    public val production: DeviceState<NumericalValue<U>> = consumation.map { consume ->
        NumericalValue(consume.values.sumOf { it.value })
    }

    public val producer: MaterialFlowProducer<U> = model(MaterialFlowProducer(context, production, consumerRequest))


    public val consumers: Map<String, MaterialFlowConsumer<U>> = supplyRequest.mapValues { (name, input) ->
        MaterialFlowConsumer(context, outputCapacity, input)
    }
}