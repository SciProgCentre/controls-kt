package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface SeparationRule<U : UnitsOfMatter, T : Amount<U>> {
    public val productionKeys: Collection<String>

    public fun forward(input: PerSecond<U, T>): Map<String, PerSecond<U, T>>

    public fun backward(output: Map<String, AmountPerSecond<U>>): AmountPerSecond<U>

    public companion object {
        public fun <U : UnitsOfMatter, T : Amount<U>> proportional(
            algebra: AmountAlgebra<U, T>,
            fractions: Map<String, Double>,
        ): SeparationRule<U, T> = object : SeparationRule<U, T> {

            private val norm = fractions.values.sum()

            override val productionKeys: Collection<String> get() = fractions.keys

            override fun forward(input: PerSecond<U, T>): Map<String, PerSecond<U, T>> = fractions.mapValues {
                with(algebra) {
                    input * (it.value / norm)
                }
            }

            override fun backward(
                output: Map<String, AmountPerSecond<U>>
            ): AmountPerSecond<U> = output.minOf { (key, value) ->
                PerSecond(value.valuePerSecond / (fractions.getValue(key) / norm))
            }

        }

    }
}

public class ContinuousSeparate<U : UnitsOfMatter, T : Amount<U>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U, T>,
    public val rule: SeparationRule<U, T>,
) : ModelConstructor(context), ContinuousConsumerInterface<U, T> {

    override val supplyRequest: LateBindDeviceState<PerSecond<U, T>> =
        LateBindDeviceState(consumerAlgebra.zero.perSecond)

    public val consumationRequest: Map<String, LateBindDeviceState<AmountPerSecond<U>>> =
        rule.productionKeys.associateWith {
            LateBindDeviceState(PerSecond.zero())
        }

    private val jointConsumationRequest: DeviceState<Map<String, AmountPerSecond<U>>> =
        combineState(consumationRequest) {
            it
        }

    private val balance: DeviceState<Pair<PerSecond<U, T>, Map<String, PerSecond<U, T>>>> = combineState(
        first = supplyRequest,
        second = jointConsumationRequest
    ) { supply: PerSecond<U, T>, consumationReq: Map<String, AmountPerSecond<U>> ->
        val expectation = rule.forward(supply)
        val limitingFactor = expectation.minOfOrNull { (key, value) ->
            (consumationReq[key] ?: PerSecond.zero()).value / value.value
        } ?: 1.0
        with(consumerAlgebra) {
            supply * limitingFactor to expectation.mapValues { (key, value) -> value * limitingFactor }
        }
    }

    public val production: DeviceState<Map<String, PerSecond<U, T>>> = mapState(balance) {
        it.second
    }

    public val individualProduction: Map<String, DeviceState<PerSecond<U, T>>> =
        rule.productionKeys.associateWith { key ->
            mapState(production) { it[key] ?: consumerAlgebra.zero.perSecond }
        }

    public val productionCapacity: DeviceState<Map<String, PerSecond<U, T>>> = combineState(
        first = supplyRequest,
        second = jointConsumationRequest
    ) { supply: PerSecond<U, T>, consumation: Map<String, AmountPerSecond<U>> ->
        with(consumerAlgebra) {
            val productionLimit = rule.backward(consumation)
            val expectedProduction = supply.coerceValueIn(zero..productionLimit)
            rule.forward(expectedProduction)
        }
    }

    public val individualProductionCapacity: Map<String, DeviceState<PerSecond<U, T>>> =
        rule.productionKeys.associateWith { key ->
            mapState(productionCapacity) { it[key] ?: consumerAlgebra.zero.perSecond }
        }

    override val consumation: DeviceState<PerSecond<U, T>> = mapState(balance) { it.first }

    override val consumationCapacity: DeviceState<AmountPerSecond<U>> = mapState(jointConsumationRequest) {
        rule.backward(it)
    }

}

public fun <U : UnitsOfMatter, T: Amount<U>> ContinuousSeparate<U, T>.asProducer(
    key: String
): ContinuousProducerInterface<U, T> = consumationRequest[key]?.let { specificConsumationRequest ->
    object : ContinuousProducerInterface<U, T> {
        override val producerAlgebra: AmountAlgebra<U, T> get() = this@asProducer.consumerAlgebra
        override val production: DeviceState<PerSecond<U, T>> get() = individualProduction[key]!!
        override val productionCapacity: DeviceState<PerSecond<U, T>> = individualProductionCapacity[key]!!
        override val consumerRequest: LateBindDeviceState<AmountPerSecond<U>> get() = specificConsumationRequest
    }
} ?: error("No supplier with key $key found")


public fun <U : UnitsOfMatter, T : Amount<U>> ContinuousFlowModel.separator(
    algebra: AmountAlgebra<U, T>,
    separationRule: SeparationRule<U, T>
): ContinuousSeparate<U, T> = model(ContinuousSeparate<U, T>(context, algebra, separationRule))