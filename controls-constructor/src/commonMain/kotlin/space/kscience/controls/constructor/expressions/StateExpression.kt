package space.kscience.controls.constructor.expressions

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.api.*
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
import kotlin.math.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * A tree of expressions that can be evaluated to a value
 */
@Serializable
public sealed interface StateExpression {
    public val dependencies: Set<StateExpression>

    @Serializable
    @SerialName("unary")
    public data class Unary(
        public val operation: String,
        public val argument: StateExpression,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = setOf(argument)
    }

    @Serializable
    @SerialName("binary")
    public data class Binary(
        public val operation: String,
        public val left: StateExpression,
        public val right: StateExpression,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = left.dependencies + right.dependencies
    }

    @Serializable
    @SerialName("nary")
    public data class Nary(
        public val operation: String,
        public val arguments: Map<String, StateExpression>,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = arguments.values.toSet()
    }

    /**
     * State expression that represents a property of a device.
     */
    @Serializable
    @SerialName("property")
    public data class Property(
        public val deviceName: Name,
        public val propertyName: String,
        public val path: Name = Name.EMPTY,
        public val parameters: Meta = Meta.EMPTY
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = emptySet()
    }

    /**
     * State expression that uses state factory from context.
     */
    @Serializable
    @SerialName("state")
    public data class State(
        public val valueStateType: String,
        public val parameters: Meta,
        public val valuePath: Name = Name.EMPTY,
        public val defaultValue: Double? = null
    ) : StateExpression {
        override val dependencies: Set<StateExpression> get() = emptySet()
    }

    @Serializable
    @SerialName("constant")
    public class Constant(public val name: String, public val parameters: Meta) : StateExpression {
        override val dependencies: Set<StateExpression> get() = emptySet()
    }

    public companion object {

    }
}

/**
 * A context for evaluating [StateExpression]
 */
public class StateExpressionContext(
    public val context: Context,
    public val hub: DeviceTree,
    public val scope: CoroutineScope = context
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
            "diff", "differentiate" -> computeState(expression.argument).differentiate(scope)
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
            val device = hub.resolveDevice(expression.deviceName)

            if (expression.path.isEmpty()) {
                device.propertyAsState(expression.propertyName, MetaConverter.double, Double.NaN)
            } else {
                device.propertyAsState(expression.propertyName, MetaConverter.meta, Meta.EMPTY)
                    .map { it[expression.path].double ?: Double.NaN }
            }
        }

        is StateExpression.State -> {
            val constructor = context.request(ConstructorPlugin)

            val state = constructor.valueStateFactories[expression.valueStateType]?.build(context, expression.parameters)
                ?: error("No value state factory for type ${expression.valueStateType}")

            state.map {
                it[expression.valuePath].double
                    ?: expression.defaultValue
                    ?: Double.NaN
            }
        }

    }
}

public fun DeviceConstructor.expression(
    expression: StateExpression,
    propertyDescriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, ValueState<Double>>> =
    PropertyDelegateProvider { _: DeviceConstructor, property ->
        val name = nameOverride ?: property.name

        val descriptor = PropertyDescriptor(name).apply {
            valueType(ValueType.NUMBER)
            propertyDescriptorBuilder()
        }

        var state: ValueState<Double>? = null

        ReadOnlyProperty { _: DeviceConstructor, _ ->
            when (val currentState = state) {
                null if isStarted() -> {
                    StateExpressionContext(context, context.request(DeviceManager), this).computeState(expression)
                        .also {
                            registerProperty(MetaConverter.double, descriptor, it)
                            state = it
                        }
                }

                null -> error("Can't access expression proeperty if device is not started")
                else -> currentState
            }
        }
    }