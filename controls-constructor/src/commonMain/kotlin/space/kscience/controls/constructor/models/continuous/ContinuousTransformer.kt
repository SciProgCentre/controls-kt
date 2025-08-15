package space.kscience.controls.constructor.models.continuous

import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.Amount
import space.kscience.controls.constructor.units.AmountAlgebra
import space.kscience.controls.constructor.units.Numeric
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import space.kscience.dataforge.context.Context

public interface ContinuousTransformationRule<U1 : UnitsOfMeasurement, T : Amount<U1>, U2 : UnitsOfMeasurement, R : Amount<U2>> {
    public fun computeProduction(amount: T): R
    public fun computeConsumption(numeric: Numeric<U2>): Numeric<U1>

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
        public fun <U1 : UnitsOfMeasurement, T : Amount<U1>, U2 : UnitsOfMeasurement, R : Amount<U2>> linear(
            productionAlgebra: AmountAlgebra<U2, R>,
            production: R
        ): ContinuousTransformationRule<U1, T, U2, R> = object : ContinuousTransformationRule<U1, T, U2, R> {
            override fun computeProduction(amount: T): R = with(productionAlgebra) {
                production * amount.value
            }

            override fun computeConsumption(numeric: Numeric<U2>): Numeric<U1> =
                Numeric(numeric.value / production.value)

        }
    }
}

public class ContinuousTransformer<U1 : UnitsOfMeasurement, T : Amount<U1>, U2 : UnitsOfMeasurement, R : Amount<U2>>(
    context: Context,
    public val supplyAlgebra: AmountAlgebra<U1, T>,
    public val rule: ContinuousTransformationRule<U1, T, U2, R>,
) : ModelConstructor(context), ContinuousProducerInterface<U2, R>, ContinuousConsumerInterface<U1, T> {

    override val supplyRequest: LateBindDeviceState<T> = LateBindDeviceState(supplyAlgebra.zero)
    override val consumerRequest: LateBindDeviceState<Numeric<U2>> = LateBindDeviceState(Numeric.zero())

    override val consumation: DeviceState<T> = combineState(supplyRequest, consumerRequest) { supply, consume ->
        with(supplyAlgebra) {
            supply.coerceValueIn(supplyAlgebra.zero..rule.computeConsumption(consume))
        }
    }

    override val consumationCapacity: DeviceState<Numeric<U1>> = mapState(consumerRequest) {
        rule.computeConsumption(it)
    }

    override val production: DeviceState<R> = mapState(consumation) { rule.computeProduction(it) }

    override val productionCapacity: DeviceState<R> = mapState(supplyRequest) {
        rule.computeProduction(it)
    }
}

public fun <U1 : UnitsOfMeasurement, T : Amount<U1>, U2 : UnitsOfMeasurement, R : Amount<U2>> ContinuousFlowModel.transformer(
    supplyAlgebra: AmountAlgebra<U1, T>,
    rule: ContinuousTransformationRule<U1, T, U2, R>,
): ContinuousTransformer<U1, T, U2, R> = model(ContinuousTransformer(context, supplyAlgebra, rule))

public fun <U1 : UnitsOfMeasurement, T : Amount<U1>, U2 : UnitsOfMeasurement, R : Amount<U2>> ContinuousFlowModel.linearTransformer(
    supplyAlgebra: AmountAlgebra<U1, T>,
    productionAlgebra: AmountAlgebra<U2, R>,
    production: R
): ContinuousTransformer<U1, T, U2, R> = model(
    ContinuousTransformer(
        context = context,
        supplyAlgebra = supplyAlgebra,
        rule = ContinuousTransformationRule.linear(productionAlgebra, production)
    )
)