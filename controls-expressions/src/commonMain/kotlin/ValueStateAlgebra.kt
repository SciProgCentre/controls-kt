package space.kscience.controls.expressions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import kotlin.time.Instant

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


        private fun evaluate(samples: Map<Symbol, ValueWithTime<Double?>>): ValueWithTime<Double?> {
            val time = samples.values.maxOfOrNull { it.time } ?: Instant.DISTANT_PAST
            val values = samples.mapValues { it.value.value ?: return ValueWithTime(null, time) }
            return ValueWithTime(mst.interpret(Float64Field, values), time)
        }

        override val valueWithTime: ValueWithTime<Double?>
            get() = evaluate(args.mapValues { it.value.valueWithTime })

        override fun subscribeWithTime(): Flow<ValueWithTime<Double?>> = if (args.isEmpty()) {
            flowOf(valueWithTime)
        } else {
            combine(args.map { (symbol, state) ->
                state.subscribeWithTime().map { symbol to it }
            }) { samples -> evaluate(samples.toMap()) }
        }

        override fun toString(): String = "MstValueState(mst=\"${mst.toLatexString()}\", dependencies=${args})"
    }

    private fun registerSource(
        source: ValueState<Double?>,
        args: MutableMap<Symbol, ValueState<Double?>>,
        preferred: Symbol? = null,
        reserved: Set<Symbol> = emptySet(),
    ): Symbol {
        args.entries.firstOrNull { it.value === source }?.let { return it.key }
        var index = args.size
        var symbol = preferred ?: Symbol("_state$index")
        while (symbol in args || (symbol != preferred && symbol in reserved)) {
            symbol = Symbol("_state${index++}")
        }
        args[symbol] = source
        return symbol
    }

    private fun unaryMstTransform(
        arg: ValueState<Double?>,
        transform: MstExtendedField.(MST) -> MST
    ): MstValueState {
        if (arg is MstValueState) return MstValueState(MstExtendedField.transform(arg.mst), arg.args)
        val args = linkedMapOf<Symbol, ValueState<Double?>>()
        return MstValueState(MstExtendedField.transform(registerSource(arg, args)), args)
    }

    private fun binaryMstTransform(
        leftArg: ValueState<Double?>,
        rightArg: ValueState<Double?>,
        transform: MstExtendedField.(left: MST, right: MST) -> MST
    ): MstValueState {
        val left = leftArg as? MstValueState
        val right = rightArg as? MstValueState
        val args = left?.args?.toMutableMap() ?: linkedMapOf()
        val leftMst = left?.mst ?: registerSource(leftArg, args)
        val rightMst = if (right == null) registerSource(rightArg, args) else {
            val symbols = right.args.mapValues { (symbol, source) ->
                registerSource(source, args, symbol, right.args.keys)
            }
            if (symbols.all { (original, mapped) -> original == mapped }) right.mst
            else right.mst.interpret(MstNumericAlgebra, symbols)
        }
        return MstValueState(MstExtendedField.transform(leftMst, rightMst), args)
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
            binaryOperations: Map<String, (arg1: ValueState<Double?>, arg2: ValueState<Double?>) -> ValueState<Double?>> = emptyMap(),
            functions: Map<String, Expression<ValueState<Double?>>> = emptyMap()
        ): ValueState<Double?> {
            val algebra = ValueStateAlgebra()
            val scope = MstInterpreterContext(
                algebra = algebra,
                arguments = bindings,
                unaryOperations = unaryOperations,
                binaryOperations = binaryOperations,
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
            binaryOperations: Map<String, (arg1: ValueState<Double?>, arg2: ValueState<Double?>) -> ValueState<Double?>> = emptyMap(),
            functions: Map<String, Expression<ValueState<Double?>>> = emptyMap()
        ): ValueState<Double?> = interpret(
            expression = expression.parseMath(),
            bindings = bindings,
            unaryOperations = unaryOperations,
            binaryOperations = binaryOperations,
            functions = functions
        )

    }
}