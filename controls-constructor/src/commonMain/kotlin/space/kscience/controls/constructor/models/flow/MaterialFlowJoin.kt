package space.kscience.controls.constructor.models.flow

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context


public enum class JoinManagementStrategy {
    PROPORTIONAL,
    ORDERED
}

/**
 * @param outputCapacity production capacity
 *
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

    public val consumation: DeviceState<Map<String, NumericalValue<U>>> = DeviceState.combine(
        listOf(outputCapacity, consumerRequest, *supplyRequest.values.toTypedArray())
    ) { args ->
        val capacityValue = args[0]
        val consumerRequestValue = args[1]
        val supplyRequestValues = args.drop(2)
        when (joinManagementStrategy) {
            JoinManagementStrategy.PROPORTIONAL -> {
                val totalInput = supplyRequestValues.sumOf { it.value }
                val totalRequest = consumerRequestValue.value
                val totalCapacity = capacityValue.value
                supplyRequestValues.associate {
                    it.name to NumericalValue(it.value * totalRequest / totalCapacity)
                }
            }

            JoinManagementStrategy.ORDERED -> TODO()
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