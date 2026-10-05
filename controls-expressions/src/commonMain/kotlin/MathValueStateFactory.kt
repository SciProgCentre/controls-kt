package space.kscience.controls.expressions

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.constructor.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.kmath.expressions.Expression
import space.kscience.kmath.expressions.Symbol
import space.kscience.kmath.expressions.symbol
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


public object MathValueStateFactory : ValueStateFactory, MetaSpec() {

    public const val TYPE: String = "math"

    public val expression: MetaRef<String> by string()
    public val arguments: MetaRef<Map<String, ValueStateConfiguration>> by serializable()

    public val arg: Symbol by symbol

    public val windows: Symbol by symbol

    public fun defaultUnaryOperations(
        scope: CoroutineScope
    ): Map<String, (arg: ValueState<Double?>) -> ValueState<Double?>> = mapOf(
        "integrateMinute" to { argValue ->
            argValue.integrate(1.minutes, scope)
        },

        "integrateHour" to { argValue ->
            argValue.integrate(1.hours, scope)
        },

        "integrateDay" to { argValue ->
            argValue.integrate(1.days, scope)
        },

        "diff" to { argValue ->
            argValue.differentiate(scope)
        }
    )

    public fun defaultFunctions(scope: CoroutineScope): Map<String, Expression<ValueState<Double?>>> = mapOf(
        "integrate" to Expression { args ->
            val argValue = args[arg] ?: error("Integrate argument is missing")
            val duration = args[windows]?.value?.seconds ?: error("Duration argument is missing")
            argValue.integrate(duration, scope)
        }
    )


    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {

        val constructorManager =
            context.plugins[ConstructorPlugin] ?: error("Constructor plugin is not found in context")
        val expression = meta[expression] ?: error("Expression is not defined in meta")

        val arguments = meta[arguments]?.mapValues { (key, configuration) ->
            constructorManager.buildValueState(configuration)
        } ?: emptyMap()


        return ValueStateAlgebra.interpret(
            expression = expression,
            bindings = arguments.entries.associate { (key, value) -> Symbol(key) to value.map { it.double } },
            unaryOperations = defaultUnaryOperations(context),
            functions = defaultFunctions(context)
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
