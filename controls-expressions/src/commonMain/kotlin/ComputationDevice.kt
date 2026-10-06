package space.kscience.controls.expressions

import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.constructor.*
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.context.resolve
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.kmath.ast.parseMath
import space.kscience.kmath.expressions.Expression
import space.kscience.kmath.expressions.MST
import space.kscience.kmath.expressions.Symbol


/**
 * A device that performs a specific computation on bound args
 * @param argNames The names of the arguments that the computation device will use.
 * @param expression The expression that defines the computation to be performed.
 */
public class ComputationDevice(
    context: Context,
    public val argNames: Set<String>,
    public val expression: Expression<ValueState<Double?>>,
    meta: Meta = Meta.EMPTY
) : DeviceConstructor(context, meta), BoundStateHolder {

    private val args: Map<String, LateBindValueState<Double?>> = argNames.associateWith { LateBindValueState(null) }

    override fun bind(state: ValueState<Meta>, inputName: String) {
        args[inputName]?.bind(state.map { it.double }) ?: error("Input name $inputName not defined in $argNames")
    }

    //args need to be declared in advance to allow creating result before binding
    public val result: ValueState<Double?> = expression(args.mapKeys { Symbol(it.key) })


    init {
        registerProperty(
            name = "result",
            converter = MetaConverter.double.nullable(),
            state = result
        )
    }

    public companion object : DeviceFactory, MetaSpec() {

        /**
         * Creates a computation device that uses MST expression to compute value
         */
        public fun ofMath(
            context: Context,
            mst: MST,
            argNames: Collection<String>,
        ): ComputationDevice {
            val expressionPlugin = context.request(ControlsExpressionPlugin)

            return ComputationDevice(
                context = context,
                argNames = argNames.toSet(),
                expression = Expression { args ->
                    ValueStateAlgebra.interpret(
                        expression = mst,
                        bindings = args,
                        unaryOperations = expressionPlugin.unaryOperations(),
                        binaryOperations = expressionPlugin.binaryOperations(),
                        functions = expressionPlugin.functions()
                    )
                }
            )
        }

        public fun ofExpression(
            context: Context,
            expression: ValueStateExpression,
            argNames: Collection<String>
        ): ComputationDevice = ComputationDevice(
            context = context,
            argNames = argNames.toSet(),
            expression = Expression { args: Map<Symbol, ValueState<Double?>> ->
                val expressionScope = StateExpressionContext(context) {
                    args[Symbol(it)] ?: error("Undefined symbol: $it")
                }

                expressionScope.computeState(expression)
            }
        )

        override val descriptor: MetaDescriptor = super<MetaSpec>.descriptor

        public val formula: MetaRef<String> by string()

        public val argNames: MetaRef<List<String>> by stringList()

        override fun buildDevice(
            context: Context,
            meta: Meta
        ): ComputationDevice {
            val argNames = meta[argNames] ?: error("Argument names not defined")

            //TODO add possibility to use expressions instead of formula
            val formula = meta[formula] ?: error("Formula not defined")
            return ofMath(context, formula.parseMath(), argNames)
        }
    }
}