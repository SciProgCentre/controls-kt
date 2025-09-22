package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface SeparationRule<U : UnitsOfMeasurement, T : Amount<U>> {
    public val productionKeys: Collection<String>

    public fun forward(input: T): Map<String, T>

    public fun backward(output: Map<String, Numeric<U>>): Numeric<U>

    public companion object {
        public fun <U : UnitsOfMeasurement, T : Amount<U>> proportional(
            algebra: AmountAlgebra<U, T>,
            fractions: Map<String, Double>,
        ): SeparationRule<U, T> = object : SeparationRule<U, T> {

            private val norm = fractions.values.sum()

            override val productionKeys: Collection<String> get() = fractions.keys

            override fun forward(input: T): Map<String, T> = fractions.mapValues {
                with(algebra) {
                    input * (it.value / norm)
                }
            }

            override fun backward(
                output: Map<String, Numeric<U>>
            ): Numeric<U> = output.minOf { (key, value) ->
                value / (fractions.getValue(key) / norm)
            }

        }

    }
}

public class ContinuousSeparate<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U, T>,
    public val rule: SeparationRule<U, T>,
) : ModelConstructor(context), ContinuousConsumerInterface<U, T> {

    override val supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(consumerAlgebra.zero)

    public val consumationRequest: Map<String, LateBindDeviceState<Numeric<U>>> = rule.productionKeys.associateWith {
        LateBindDeviceState(Numeric.zero())
    }

    private val jointConsumationRequest: DeviceState<Map<String, Numeric<U>>> = combineState(consumationRequest) {
        it
    }

    private val balance: DeviceState<Pair<T, Map<String, T>>> = combineState(
        first = supplyRequest,
        second = jointConsumationRequest
    ) { supply: T, consumation: Map<String, Numeric<U>> ->
        val expectation = rule.forward(supply)
        val limitingFactor = expectation.minOfOrNull { (key, value) ->
            (consumation[key] ?: Numeric.zero()).value / value.value
        } ?: 0.0
        with(consumerAlgebra) {
            supply * limitingFactor to expectation.mapValues { (key, value) -> supply * limitingFactor }
        }
    }

    public val production: DeviceState<Map<String, T>> = mapState(balance) { it.second }

    public val individualProduction: Map<String, DeviceState<T>> = rule.productionKeys.associateWith { key ->
        mapState(production) { it[key] ?: consumerAlgebra.zero }
    }

    public val productionCapacity: DeviceState<Map<String, T>> = combineState(
        first = supplyRequest,
        second = jointConsumationRequest
    ) { supply: T, consumation: Map<String, Numeric<U>> ->
        with(consumerAlgebra) {
            val productionLimit = rule.backward(consumation)
            val expectedProduction = supply.coerceValueIn(zero..productionLimit)
            rule.forward(expectedProduction)
        }
    }

    public val individualProductionCapacity: Map<String, DeviceState<T>> = rule.productionKeys.associateWith { key ->
        mapState(productionCapacity) { it[key] ?: consumerAlgebra.zero }
    }

    override val consumation: DeviceState<T> = mapState(balance) { it.first }

    override val consumationCapacity: DeviceState<Numeric<U>> = mapState(jointConsumationRequest) {
        rule.backward(it)
    }

}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousSeparate<U, T>.asProducer(
    key: String
): ContinuousProducerInterface<U, T> = consumationRequest[key]?.let { specificConsumationRequest ->
    object : ContinuousProducerInterface<U, T> {
        override val producerAlgebra: AmountAlgebra<U, T> get() = this@asProducer.consumerAlgebra
        override val production: DeviceState<T> get() = individualProduction[key]!!
        override val productionCapacity: DeviceState<T> = individualProductionCapacity[key]!!
        override val consumerRequest: LateBindDeviceState<Numeric<U>> get() = specificConsumationRequest
    }
} ?: error("No supplier with key $key found")


public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousFlowModel.separator(
    algebra: AmountAlgebra<U, T>,
    separationRule: SeparationRule<U, T>
): ContinuousSeparate<U, T> = model(ContinuousSeparate<U, T>(context, algebra, separationRule))