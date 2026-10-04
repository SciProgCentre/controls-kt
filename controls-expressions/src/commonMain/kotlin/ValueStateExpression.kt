package space.kscience.controls.constructor.expressions

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.controls.api.*
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
import kotlin.math.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * A tree of expressions that can be evaluated to a value
 */
@Serializable
public sealed interface ValueStateExpression {
    public val dependencies: Set<ValueStateExpression>

    @Serializable
    @SerialName("unary")
    public data class Unary(
        public val operation: String,
        public val argument: ValueStateExpression,
        public val parameters: Meta = Meta.EMPTY
    ) : ValueStateExpression {
        override val dependencies: Set<ValueStateExpression> get() = setOf(argument)
    }

    @Serializable
    @SerialName("binary")
    public data class Binary(
        public val operation: String,
        public val left: ValueStateExpression,
        public val right: ValueStateExpression,
        public val parameters: Meta = Meta.EMPTY
    ) : ValueStateExpression {
        override val dependencies: Set<ValueStateExpression> get() = left.dependencies + right.dependencies
    }

    @Serializable
    @SerialName("nary")
    public data class Nary(
        public val operation: String,
        public val arguments: Map<String, ValueStateExpression>,
        public val parameters: Meta = Meta.EMPTY
    ) : ValueStateExpression {
        override val dependencies: Set<ValueStateExpression> get() = arguments.values.toSet()
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
    ) : ValueStateExpression {
        override val dependencies: Set<ValueStateExpression> get() = emptySet()
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
    ) : ValueStateExpression {
        override val dependencies: Set<ValueStateExpression> get() = emptySet()
    }

    @Serializable
    @SerialName("constant")
    public class Constant(public val name: String, public val parameters: Meta) : ValueStateExpression {
        override val dependencies: Set<ValueStateExpression> get() = emptySet()
    }
}

/**
 * A context for evaluating [ValueStateExpression]
 */
public class StateExpressionContext(
    public val context: Context,
    public val hub: DeviceTree,
    public val scope: CoroutineScope = context
) {
    public fun computeState(expression: ValueStateExpression): ValueState<Double?> = when (expression) {

        is ValueStateExpression.Unary -> when (expression.operation) {
            "-", "negate", "negative" -> computeState(expression.argument).map {
                if (it == null) return@map null
                -it
            }

            "sin" -> computeState(expression.argument).map {
                if (it == null) return@map null
                sin(it)
            }

            "cos" -> computeState(expression.argument).map {
                if (it == null) return@map null
                cos(it)
            }

            "abs" -> computeState(expression.argument).map {
                if (it == null) return@map null
                it.absoluteValue
            }

            "sqrt" -> computeState(expression.argument).map {
                if (it == null) return@map null
                sqrt(it)
            }

            "exp" -> computeState(expression.argument).map {
                if (it == null) return@map null
                exp(it)
            }

            "ln" -> computeState(expression.argument).map {
                if (it == null) return@map null
                ln(it)
            }

            "diff", "differentiate" -> computeState(expression.argument).differentiate(scope)
            else -> error("Unknown unary operation: ${expression.operation}")
        }

        is ValueStateExpression.Binary -> when (expression.operation) {
            "+", "plus" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                if (l == null || r == null) return@combine null
                l + r
            }

            "-", "minus" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                if (l == null || r == null) return@combine null
                l - r
            }

            "*", "times", "multiply" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                if (l == null || r == null) return@combine null
                l * r
            }

            "/", "div", "divide" -> ValueState.combine(
                scope = scope,
                state1 = computeState(expression.left),
                state2 = computeState(expression.right)
            ) { l, r ->
                if (l == null || r == null) return@combine null
                l / r
            }

            else -> error("Unknown binary operation: ${expression.operation}")
        }

        is ValueStateExpression.Nary -> when (expression.operation) {
            "sum" -> ValueState.combine(
                scope = scope,
                states = expression.arguments.values.map { computeState(it) }
            ) {
                it.filterNotNull().sum()
            }

            "mean", "average" -> ValueState.combine(
                scope = scope,
                states = expression.arguments.values.map { computeState(it) }
            ) {
                val values = it.filterNotNull()
                if (values.isEmpty()) null else values.average()
            }

            else -> error("Unknown Nary operation: ${expression.operation}")
        }

        is ValueStateExpression.Constant -> when (expression.name) {
            "pi", "Pi", "PI" -> ValueState(PI)
            "e" -> ValueState(E)
            else -> expression.parameters["value"]?.double?.let { ValueState(it) }
                ?: error("Unknown constant: ${expression.name}")
        }

        is ValueStateExpression.Property -> {
            val device = hub.resolveDevice(expression.deviceName)

            if (expression.path.isEmpty()) {
                device.propertyAsState(expression.propertyName, MetaConverter.double.nullable(), null)
            } else {
                device.propertyAsState(expression.propertyName, MetaConverter.meta, Meta.EMPTY)
                    .map { it[expression.path].double }
            }
        }

        is ValueStateExpression.State -> {
            val constructor = context.request(ConstructorPlugin)

            val state = constructor.buildValueState(expression.parameters, expression.valueStateType)

            state.map {
                it[expression.valuePath].double ?: expression.defaultValue
            }
        }

    }
}

/**
 * Factory for creating instances of [ValueState] based on state expressions.
 *
 * This class represents a factory that processes a [ValueStateExpression]
 * within a given context to produce a corresponding [ValueState]. It serves
 * as a connection between high-level metadata and the underlying observable
 * state values.
 *
 * The factory integrates with the application context, where it resolves
 * dependencies such as the [DeviceManager]. It uses a dedicated
 * [StateExpressionContext] to evaluate state expressions and compute the
 * observable state corresponding to those expressions.
 *
 * The factory expects a `Meta` object containing the state expression as
 * input and ensures that the required components are available in the provided
 * context. If necessary dependencies are missing, the factory throws errors
 * to indicate the misconfiguration.
 *
 * Key features:
 * - Processes a [ValueStateExpression] from metadata to compute a [ValueState].
 * - Manages dependencies through the [DeviceManager] plugin in the context.
 * - Supports the evaluation of expressions using the [StateExpressionContext].
 *
 * Properties:
 * - `expression`: References the [ValueStateExpression] metadata item used
 *   to evaluate and compute the state.
 *
 * Implements:
 * - [ValueStateFactory]: For constructing [ValueState] instances.
 * - [MetaSpec]: For managing metadata specifications.
 */
public object ExpressionValueStateFactory : ValueStateFactory, MetaSpec() {

    public const val TYPE: String = "expression"

    public val expressionConverter: MetaConverter<ValueStateExpression> =
        MetaConverter.serializable<ValueStateExpression>()

    public val expression: MetaRef<ValueStateExpression> by item(expressionConverter)

    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {
        val expression = meta[expression] ?: error("Expression not defined")

        val deviceManager = context.plugins[DeviceManager] ?: error("Device manager is not found in context")

        val expressionScope = StateExpressionContext(context, deviceManager)

        return expressionScope.computeState(expression).map {
            if (it == null) Meta.EMPTY else Meta(it)
        }
    }
}

public fun ValueStateConfiguration.Companion.expression(
    expression: ValueStateExpression
): ValueStateConfiguration = ValueStateConfiguration(
    ExpressionValueStateFactory.TYPE,
    Meta {
        set(ExpressionValueStateFactory.expression, expression)
    })

public fun DeviceConstructor.expression(
    expression: ValueStateExpression,
    propertyDescriptorBuilder: PropertyDescriptor.() -> Unit = {},
    nameOverride: String? = null,
): PropertyDelegateProvider<DeviceConstructor, ReadOnlyProperty<DeviceConstructor, ValueState<Double?>>> =
    PropertyDelegateProvider { _: DeviceConstructor, property ->
        val name = nameOverride ?: property.name

        val descriptor = PropertyDescriptor(name).apply {
            valueType(ValueType.NUMBER)
            propertyDescriptorBuilder()
        }

        var state: ValueState<Double?>? = null

        ReadOnlyProperty { _: DeviceConstructor, _ ->
            when (val currentState = state) {
                null if isStarted() -> {
                    StateExpressionContext(context, context.request(DeviceManager), this).computeState(expression)
                        .also {
                            registerProperty(MetaConverter.double.nullable(), descriptor, it)
                            state = it
                        }
                }

                null -> error("Can't access expression proeperty if device is not started")
                else -> currentState
            }
        }
    }
