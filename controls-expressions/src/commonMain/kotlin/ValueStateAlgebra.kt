package space.kscience.controls.expressions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateWithDependencies
import space.kscience.controls.time.ValueWithTime
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
    private class MstValueState(
        val mst: MST,
        val args: Map<Symbol, ValueState<Double?>>
    ) : ValueStateWithDependencies<Double?> {

        override val dependencies: Collection<ValueState<*>> get() = args.values

        override val value: Double?
            get() {
                val dependencyValues = args.mapValues { it.value.value ?: return null }
                return mst.interpret(Float64Field, dependencyValues)
            }


        override val valueWithTime: ValueWithTime<Double?>
            get() {
                val dependencyValues = args.mapValues { it.value.valueWithTime }
                val time = dependencyValues.maxOf { it.value.time }
                val value = mst.interpret(
                    algebra = Float64Field,
                    arguments = args.mapValues { it.value.value ?: return ValueWithTime(null, time) }
                )
                return ValueWithTime(value, time)
            }

        override fun subscribeWithTime(): Flow<ValueWithTime<Double?>> =
            args.values.map { it.subscribe() }.merge().map { valueWithTime }

        override fun toString(): String = "MstValueState(mst=\"${mst.toLatexString()}\", dependencies=${args})"
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
        return MstValueState(MstExtendedField.transform(mstArg.mst), mstArg.args)
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
            leftMstArg.args + rightMstArg.args
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
        public fun interpret(
            expression: MST,
            bindings: Map<Symbol, ValueState<Double?>>,
            unaryOperations: Map<String, (arg: ValueState<Double?>) -> ValueState<Double?>> = emptyMap(),
            functions: Map<String, Expression<ValueState<Double?>>> = emptyMap()
        ): ValueState<Double?> {
            val algebra = ValueStateAlgebra()
            val scope = MstInterpreterContext(
                algebra = algebra,
                arguments = bindings,
                unaryOperations = unaryOperations,
                functions = functions,
            )
            return context(scope) {
                expression.interpret()
            }
        }

        /**
         * Interpret a mathematical expression represented as a string and evaluate it using the provided bindings.
         */
        public fun interpret(
            expression: String,
            bindings: Map<Symbol, ValueState<Double?>>,
            unaryOperations: Map<String, (arg: ValueState<Double?>) -> ValueState<Double?>> = emptyMap(),
            functions: Map<String, Expression<ValueState<Double?>>> = emptyMap()
        ): ValueState<Double?> = interpret(
            expression = expression.parseMath(),
            bindings = bindings,
            unaryOperations = unaryOperations,
            functions = functions
        )

    }
}