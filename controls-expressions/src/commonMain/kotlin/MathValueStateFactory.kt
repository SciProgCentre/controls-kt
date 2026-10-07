package space.kscience.controls.expressions

import space.kscience.controls.constructor.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.kmath.expressions.Symbol
import space.kscience.kmath.expressions.symbol


public object MathValueStateFactory : ValueStateFactory, MetaSpec() {

    public const val TYPE: String = "math"

    public val expression: MetaRef<String> by string()
    public val arguments: MetaRef<Map<String, ValueStateConfiguration>> by serializable()

    public val arg: Symbol by symbol

    public val windows: Symbol by symbol

    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {

        val expressionPlugin =
            context.plugins[ControlsExpressionPlugin] ?: error("Constructor expression plugin is not found in context")


        val expression = meta[expression] ?: error("Expression is not defined in meta")

        val arguments = meta[arguments]?.mapValues { (key, configuration) ->
            expressionPlugin.constructor.buildValueState(configuration)
        } ?: emptyMap()


        return ValueStateAlgebra.interpret(
            expression = expression,
            bindings = arguments.entries.associate { (key, value) -> Symbol(key) to value.map { it.double } },
            unaryOperations = expressionPlugin.unaryOperations,
            binaryOperations = expressionPlugin.binaryOperations,
            functions = expressionPlugin.functions
        ).map { if (it == null) Meta.EMPTY else Meta(it) }
    }
}

/**
 * Create configuration to invoke for MathValueStateFactory
 */
public fun ValueStateConfiguration.Companion.math(
    expression: String,
    arguments: Map<String, ValueStateConfiguration> = emptyMap()
): ValueStateConfiguration = ValueStateConfiguration(MathValueStateFactory.TYPE, Meta {
    set(MathValueStateFactory.expression, expression)
    set(MathValueStateFactory.arguments, arguments)
})
