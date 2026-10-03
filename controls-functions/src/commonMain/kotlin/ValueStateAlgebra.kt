package space.kscience.controls.expressions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.transformNotNull
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.kmath.ast.parseMath
import space.kscience.kmath.ast.rendering.FeaturedMathRendererWithPostProcess
import space.kscience.kmath.ast.rendering.LatexSyntaxRenderer
import space.kscience.kmath.ast.rendering.renderWithStringBuilder
import space.kscience.kmath.expressions.*
import space.kscience.kmath.operations.ExtendedField
import space.kscience.kmath.operations.Float64Field
import space.kscience.kmath.structures.MutableBufferFactory

/**
 * A class that represents a mathematical expression evaluation system for observable value states.
 * It implements multiple interfaces to provide algebraic operations, extended field operations, and symbol indexing.
 */
public class ValueStateAlgebra : ExpressionAlgebra<Double?, ValueState<Double?>>, ExtendedField<ValueState<Double?>> {

    /**
     * A value state that holds MST expression and dependencies and computes value on demand
     */
    private inner class MstValueState(
        val mst: MST,
        val dependencies: Map<Symbol, ValueState<Double?>>
    ) : ValueState<Double?> {

        override val value: Double?
            get() {
                val dependencyValues = dependencies.mapValues { it.value.value ?: return null }
                return mst.interpret(Float64Field, dependencyValues)
            }


        override val valueWithTime: ValueWithTime<Double?>
            get() {
                val dependencyValues = dependencies.mapValues { it.value.valueWithTime }
                val time = dependencyValues.maxOf { it.value.time }
                val value = mst.interpret(
                    algebra = Float64Field,
                    arguments = dependencies.mapValues { it.value.value ?: return ValueWithTime(null, time) }
                )
                return ValueWithTime(value, time)
            }

        override fun subscribeWithTime(): Flow<ValueWithTime<Double?>> =
            dependencies.values.map { it.subscribe() }.merge().map { valueWithTime }

        override fun toString(): String = "MstValueState(mst=\"${mst.toLatexString()}\", dependencies=${dependencies})"
    }

    /**
     * Wrap arbitrary value state into MstValueState
     */
    private fun wrapValueState(arg: ValueState<Double?>): MstValueState = if (arg is MstValueState) {
        arg
    } else {
        val argSymbol = Symbol(arg.hashCode().toHexString())
        MstValueState(argSymbol, mapOf(argSymbol to arg))
    }

    private fun unaryMstTransform(
        arg: ValueState<Double?>,
        transform: MstExtendedField.(MST) -> MST
    ): MstValueState {
        val mstArg = wrapValueState(arg)
        return MstValueState(MstExtendedField.transform(mstArg.mst), mstArg.dependencies)
    }

    private fun binaryMstTransform(
        leftArg: ValueState<Double?>,
        rightArg: ValueState<Double?>,
        transform: MstExtendedField.(left: MST, right: MST) -> MST
    ): MstValueState {
        val leftMstArg = wrapValueState(leftArg)
        val rightMstArg = wrapValueState(rightArg)
        return MstValueState(
            MstExtendedField.transform(leftMstArg.mst, rightMstArg.mst),
            leftMstArg.dependencies + rightMstArg.dependencies
        )
    }


    override fun const(value: Double?): ValueState<Double?> =
        MstValueState(MST.Numeric(value ?: Double.NaN), emptyMap())

    override fun number(value: Number): ValueState<Double?> = const(value.toDouble())

    override val bufferFactory: MutableBufferFactory<ValueState<Double?>> = MutableBufferFactory()

    override fun divide(
        left: ValueState<Double?>,
        right: ValueState<Double?>
    ): ValueState<Double?> = binaryMstTransform(left, right) { left, right -> left / right }

    override fun multiply(
        left: ValueState<Double?>,
        right: ValueState<Double?>
    ): ValueState<Double?> = binaryMstTransform(left, right) { left, right -> left * right }

    override fun add(
        left: ValueState<Double?>,
        right: ValueState<Double?>
    ): ValueState<Double?> = binaryMstTransform(left, right) { left, right -> left + right }

    override fun ValueState<Double?>.unaryMinus(): ValueState<Double?> = unaryMstTransform(this) { it.unaryMinus() }

    override fun sin(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { sin(it) }

    override fun cos(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { cos(it) }

    override fun asin(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { asin(it) }

    override fun acos(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { acos(it) }

    override fun atan(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { atan(it) }

    override fun exp(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { exp(it) }

    override fun ln(arg: ValueState<Double?>): ValueState<Double?> = unaryMstTransform(arg) { ln(it) }

    override fun scale(
        a: ValueState<Double?>,
        value: Double
    ): ValueState<Double?> = unaryMstTransform(a) { it * value }

    override fun power(
        arg: ValueState<Double?>,
        pow: Number
    ): ValueState<Double?> = unaryMstTransform(arg) { it.pow(pow.toDouble()) }

    override val one: ValueState<Double?> = const(1.0)

    override val zero: ValueState<Double?> = const(0.0)

    public companion object {

        private fun MST.toLatexString() =
            LatexSyntaxRenderer.renderWithStringBuilder(FeaturedMathRendererWithPostProcess.Default.render(this))


        /**
         * Interpret a mathematical expression represented as an MST (Mathematical Syntax Tree) and evaluate it using the provided bindings.
         */
        public fun interpret(expression: MST, bindings: Map<Symbol, ValueState<Double?>>): ValueState<Double?> {
            val algebra = ValueStateAlgebra()
            val scope = MstInterpreterContext(algebra, bindings)
            return context(scope) {
                expression.interpret()
            }
        }

        /**
         * Interpret a mathematical expression represented as a string and evaluate it using the provided bindings.
         */
        public fun interpret(expression: String, bindings: Map<Symbol, ValueState<Double?>>): ValueState<Double?> =
            interpret(expression.parseMath(), bindings)

    }
}