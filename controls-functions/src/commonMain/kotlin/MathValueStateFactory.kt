package space.kscience.controls.expressions

import kotlinx.serialization.Serializable
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.constructor.map
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.*
import space.kscience.kmath.expressions.Symbol


public object MathValueStateFactory : ValueStateFactory, MetaSpec() {

    @Serializable
    public data class DependencySpec(val symbol: String, val configuration: Meta)

    public val expression: MetaRef<String> by string()
    public val dependencies: MetaRef<List<DependencySpec>> by serializable()

    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {

        val constructorManager =
            context.plugins[ConstructorPlugin] ?: error("Constructor plugin is not found in context")
        val expression = meta[expression] ?: error("Expression is not defined in meta")

        val dependencies = meta[dependencies]?.associate {
            it.symbol to constructorManager.buildValueState(it.configuration)
        } ?: emptyMap()


        return ValueStateAlgebra.interpret(
            expression = expression,
            bindings = dependencies.entries.associate { (key, value) -> Symbol(key) to value.map { it.double } }
        ).map { if (it == null) Meta.EMPTY else Meta(it) }
    }

}
