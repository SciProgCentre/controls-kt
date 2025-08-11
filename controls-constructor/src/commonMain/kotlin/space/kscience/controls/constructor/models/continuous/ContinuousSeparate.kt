package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.AmountAlgebra
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

public interface SeparationRule<U : UnitsOfMeasurement, T : Amount<U>> {
    public val productionKeys: Collection<String>

    public fun forward(input: T): Map<String, T>

    public fun backward(output: Map<String, Numeric<U>>): Numeric<U>
}

public class ContinuousSeparate<U : UnitsOfMeasurement, T : Amount<U>>(
    context: Context,
    public val algebra: AmountAlgebra<U, T>,
    public val rule: SeparationRule<U, T>,
) : ModelConstructor(context), ContinuousConsumerInterface<U, T> {

    override val supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(this, algebra.zero)

    public val consumationRequest: Map<String, LateBindDeviceState<Numeric<U>>> = rule.productionKeys.associateWith {
        LateBindDeviceState(this, Numeric.zero())
    }

    private val jointConsumationRequest: DeviceState<Map<String, Numeric<U>>> = combineState(consumationRequest) {
        it
    }

    private val balance: DeviceState<Pair<T, Map<String, T>>> = combineState(
        first = supplyRequest,
        second = jointConsumationRequest
    ) { supply: T, consumation: Map<String, Numeric<U>> ->
        val expectation = rule.forward(supply)
        val limitingFactor = expectation.minOf { (key, value) ->
            (consumation[key] ?: Numeric.zero()).value / value.value
        }
        with(algebra) {
            supply * limitingFactor to expectation.mapValues { (key, value) -> supply * limitingFactor }
        }
    }

    public val production: DeviceState<Map<String, T>> = mapState(balance) { it.second }

    public val individualProduction: Map<String, DeviceState<T>> = rule.productionKeys.associateWith { key ->
        mapState(production) { it[key]!! }
    }

    public val productionCapacity: DeviceState<Map<String, T>> = mapState(supplyRequest) {
        rule.forward(it)
    }

    public val individualProductionCapacity: Map<String, DeviceState<T>> = rule.productionKeys.associateWith { key ->
        mapState(productionCapacity) { it[key]!! }
    }

    override val consumation: DeviceState<T> = mapState(balance) { it.first }

    override val consumationCapacity: DeviceState<Numeric<U>> = mapState(jointConsumationRequest) {
        rule.backward(it)
    }

}

public fun <U : UnitsOfMeasurement, T : Amount<U>> ContinuousSeparate<U, T>.asConsumer(
    key: String
): ContinuousProducerInterface<U, T> = consumationRequest[key]?.let { specificConsumationRequest ->
    object : ContinuousProducerInterface<U, T> {
        override val production: DeviceState<T> get() = individualProduction[key]!!
        override val productionCapacity: DeviceState<T> = individualProductionCapacity[key]!!
        override val consumerRequest: LateBindDeviceState<Numeric<U>> get() = specificConsumationRequest
    }
} ?: error("No supplier with key $key found")