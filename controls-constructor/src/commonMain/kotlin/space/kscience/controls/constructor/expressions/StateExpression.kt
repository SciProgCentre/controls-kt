package space.kscience.controls.constructor.expressions

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.combine
import space.kscience.controls.constructor.map
import space.kscience.controls.constructor.propertyAsState
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
import kotlin.math.*

/**
 * A tree of expressions that can be evaluated to a value
 */
@Serializable
public sealed interface StateExpression {
    public val dependencies: Set<StateExpression>

    @Serializable
    public data class Unary(
        public val operation: String,
        public val argument: StateExpression,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = setOf(argument)
    }

    @Serializable
    public data class Binary(
        public val operation: String,
        public val left: StateExpression,
        public val right: StateExpression,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = left.dependencies + right.dependencies
    }

    @Serializable
    public data class Nary(
        public val operation: String,
        public val arguments: Map<String, StateExpression>,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = arguments.values.toSet()
    }

    @Serializable
    public data class Property(
        public val deviceName: Name,
        public val propertyName: String,
        public val path: Name = Name.EMPTY,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = emptySet()
    }

    @Serializable
    public class Constant(public val name: String, public val parameters: Meta) : StateExpression {
        override val dependencies: Set<StateExpression> get() = emptySet()
    }
}

public class StateExpressionContext(
    public val hub: DeviceHub,
    public val scope: CoroutineScope
) {
    public fun computeState(expression: StateExpression): ValueState<Double> = when (expression) {

        is StateExpression.Unary -> when (expression.operation) {
            "-", "negate", "negative" -> computeState(expression.argument).map { -it }
            "sin" -> computeState(expression.argument).map { sin(it) }
            "cos" -> computeState(expression.argument).map { cos(it) }
            "abs" -> computeState(expression.argument).map { it.absoluteValue }
            "sqrt" -> computeState(expression.argument).map { sqrt(it) }
            "exp" -> computeState(expression.argument).map { exp(it) }
            "ln" -> computeState(expression.argument).map { ln(it) }
            else -> error("Unknown unary operation: ${expression.operation}")
        }

        is StateExpression.Binary -> when (expression.operation) {
            "+", "plus" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                l + r
            }

            "-", "minus" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                l - r
            }

            "*", "times", "multiply" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                l * r
            }

            else -> error("Unknown binary operation: ${expression.operation}")
        }

        is StateExpression.Nary -> when (expression.operation) {
            "sum" -> ValueState.combine(
                scope = scope,
                states = expression.arguments.values.map { computeState(it) }
            ) {
                it.sum()
            }

            else -> error("Unknown Nary operation: ${expression.operation}")
        }

        is StateExpression.Constant -> when (expression.name) {
            "pi", "Pi", "PI" -> ValueState(PI)
            "e" -> ValueState(E)
            else -> error("Unknown constant: ${expression.name}")
        }

        is StateExpression.Property -> {
            val device = hub.devices[expression.deviceName] ?: error("No device ${expression.deviceName} found")

            if (expression.path.isEmpty()) {
                device.propertyAsState(expression.propertyName, MetaConverter.double, Double.NaN)
            } else {
                device.propertyAsState(expression.propertyName, MetaConverter.meta, Meta.EMPTY)
                    .map { it[expression.path].double ?: Double.NaN }
            }
        }

    }
}

