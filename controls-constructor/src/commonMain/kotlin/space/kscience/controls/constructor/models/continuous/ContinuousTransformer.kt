package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.dataforge.context.Context

public interface ContinuousTransformationRule<U1 : UnitsOfMatter, T : Amount<U1>, U2 : UnitsOfMatter, R : Amount<U2>> {
    public fun computeProduction(amount: PerSecond<U1, T>): PerSecond<U2, R>
    public fun computeConsumption(numeric: AmountPerSecond<U2>): AmountPerSecond<U1>

    public companion object {
        /**
         * Creates a linear transformation rule that defines the relationship between consumption and production
         * based on a given production amount per unit of consumption and the provided algebra for computations.
         *
         * @param U1 the unit of measurement for the input amount (consumption).
         * @param T the type of the input amount (consumption) extending `Amount<U1>`.
         * @param U2 the unit of measurement for the output amount (production).
         * @param R the type of the output amount (production) extending `Amount<U2>`.
         * @param productionAlgebra the algebra used to perform calculations for the production amount.
         * @param production the fixed amount of production corresponding to one unit of consumption.
         * @return a transformation rule that maps consumption to production and vice-versa.
         */
        public fun <U1 : UnitsOfMatter, T : Amount<U1>, U2 : UnitsOfMatter, R : Amount<U2>> linear(
            productionAlgebra: AmountAlgebra<U2, R>,
            production: PerSecond<U2, R>
        ): ContinuousTransformationRule<U1, T, U2, R> = object : ContinuousTransformationRule<U1, T, U2, R> {
            override fun computeProduction(amount: PerSecond<U1, T>): PerSecond<U2, R> = with(productionAlgebra) {
                production * amount.value
            }

            override fun computeConsumption(numeric: AmountPerSecond<U2>): AmountPerSecond<U1> =
                AmountPerSecond(numeric.value / production.value)

        }
    }
}

public class ContinuousTransformer<U1 : UnitsOfMatter, T : Amount<U1>, U2 : UnitsOfMatter, R : Amount<U2>>(
    context: Context,
    override val consumerAlgebra: AmountAlgebra<U1, T>,
    override val producerAlgebra: AmountAlgebra<U2, R>,
    public val rule: ContinuousTransformationRule<U1, T, U2, R>,
) : ModelConstructor(context), ContinuousProducer<U2, R>, ContinuousConsumer<U1, T> {

    override val supplyRequest: LateBindDeviceState<PerSecond<U1, T>> =
        LateBindDeviceState(consumerAlgebra.zero.perSecond)
    override val consumerRequest: LateBindDeviceState<AmountPerSecond<U2>> = LateBindDeviceState(PerSecond.zero())

    override val consumation: DeviceState<PerSecond<U1, T>> =
        combineState(supplyRequest, consumerRequest) { supply, consume ->
            with(consumerAlgebra) {
                supply.coerceValueIn(consumerAlgebra.zero..rule.computeConsumption(consume))
            }
        }

    override val consumationCapacity: DeviceState<AmountPerSecond<U1>> = mapState(consumerRequest) {
        rule.computeConsumption(it)
    }

    override val production: DeviceState<PerSecond<U2, R>> = mapState(consumation) { rule.computeProduction(it) }

    override val productionCapacity: DeviceState<PerSecond<U2, R>> = mapState(supplyRequest) {
        rule.computeProduction(it)
    }
}

public fun <U1 : UnitsOfMatter, T : Amount<U1>, U2 : UnitsOfMatter, R : Amount<U2>> ContinuousFlowModel.transformer(
    consumerAlgebra: AmountAlgebra<U1, T>,
    producerAlgebra: AmountAlgebra<U2, R>,
    rule: ContinuousTransformationRule<U1, T, U2, R>,
): ContinuousTransformer<U1, T, U2, R> = model(ContinuousTransformer(context, consumerAlgebra, producerAlgebra, rule))

public fun <U1 : UnitsOfMatter, T : Amount<U1>, U2 : UnitsOfMatter, R : Amount<U2>> ContinuousFlowModel.linearTransformer(
    consumerAlgebra: AmountAlgebra<U1, T>,
    producerAlgebra: AmountAlgebra<U2, R>,
    production: PerSecond<U2, R>
): ContinuousTransformer<U1, T, U2, R> = model(
    ContinuousTransformer(
        context = context,
        producerAlgebra = producerAlgebra,
        consumerAlgebra = consumerAlgebra,
        rule = ContinuousTransformationRule.linear(producerAlgebra, production)
    )
)