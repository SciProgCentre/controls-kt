package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import space.kscience.controls.api.DeviceMessageSource
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.constructor.expressions.StateExpressionContext
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.Described
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.misc.DfType
import space.kscience.dataforge.names.parseAsName
import kotlin.time.Instant

/**
 * An interface for [ValueState] construction and registry
 */
@DfType(ValueStateFactory.PROVIDER_TAGET)
public interface ValueStateFactory : Factory<ValueState<Meta>>, Described {
    public companion object {
        public const val PROVIDER_TAGET: String = "factory.valueState"
    }
}

/**
 * A factory object for creating instances of [ValueState] associated with device properties.
 * It provides mechanisms to bind a device's property to a reactive state representation.
 *
 * This factory leverages context metadata and device management plugins to retrieve
 * property values dynamically, resolving devices and properties at runtime.
 *
 * ### Key Properties
 * - **deviceName**: Represents the name of the device, used for resolving the target device.
 * - **propertyName**: Represents the name of the property associated with the device.
 * - **defaultValue**: Specifies the default value to use when the property's current value is unavailable.
 *
 * ### Behavior
 * The factory attempts to:
 * 1. Resolve the target device using the provided device name.
 * 2. Resolve the property's state using the provided property name.
 * 3. Create a [ValueState] that observes and reacts to updates in the specified device property.
 *
 * @constructor This factory uses metadata and context plugins to establish property state bindings.
 */
@OptIn(DFExperimental::class)
public object DeviceValueStateFactory: ValueStateFactory, MetaSpec(){

    public val deviceName: MetaRef<String> by string()

    public val propertyName: MetaRef<String> by string()

    public val defaultValue: MetaRef<Meta> by metaItem()

    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {
        val deviceName = meta[deviceName]?.parseAsName() ?: error("Device name is not specified")
        val propertyName = meta[propertyName] ?: error("Property name is not specified")
        val deviceManager = context.plugins[DeviceManager] ?: error("Device manager is not found in context")
        val defaultValue = meta[defaultValue] ?: Meta.EMPTY
        return deviceManager.resolveDevice(deviceName).propertyAsState(propertyName, MetaConverter.meta, defaultValue)
    }

}

/**
 * Factory for creating instances of [ValueState] based on state expressions.
 *
 * This class represents a factory that processes a [StateExpression]
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
 * - Processes a [StateExpression] from metadata to compute a [ValueState].
 * - Manages dependencies through the [DeviceManager] plugin in the context.
 * - Supports the evaluation of expressions using the [StateExpressionContext].
 *
 * Properties:
 * - `expression`: References the [StateExpression] metadata item used
 *   to evaluate and compute the state.
 *
 * Implements:
 * - [ValueStateFactory]: For constructing [ValueState] instances.
 * - [MetaSpec]: For managing metadata specifications.
 */
@OptIn(DFExperimental::class)
public object ExpressionValueStateFactory: ValueStateFactory, MetaSpec(){

    public val expression: MetaRef<StateExpression> by item(MetaConverter.serializable<StateExpression>())

    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {
        val expression = meta[expression] ?: error("Expression not defined")

        val deviceManager = context.plugins[DeviceManager] ?: error("Device manager is not found in context")

        val expressionScope = StateExpressionContext(deviceManager, context)

        return expressionScope.computeState(expression).map { Meta(it) }
    }

}

/**
 * Create a [ValueStateFactory] from a [DeviceMessageSource]
 */
public fun DeviceMessageSource.asValueStateFactory(
    scope: CoroutineScope
): ValueStateFactory = object : ValueStateFactory {
    override fun build(
        context: Context,
        meta: Meta
    ): ValueState<Meta> {
        val deviceName = meta["deviceName"].string?.parseAsName() ?: error("Device name is not specified")
        val propertyName = meta["propertyName"].string ?: error("Property name is not specified")
        val defaultValue = meta["defaultValue"] ?: Meta.EMPTY

        val defaultValueWithTime = ValueWithTime(defaultValue, Instant.DISTANT_PAST)

        val valueFlow: StateFlow<ValueWithTime<Meta>> = messageFlow.filterIsInstance<PropertyChangedMessage>().filter {
            it.sourceDevice == deviceName && it.property == propertyName
        }.map {
            ValueWithTime(it.value, it.time)
        }.stateIn(scope, SharingStarted.Eagerly,defaultValueWithTime)

        return object : ValueState<Meta>{
            override val valueWithTime: ValueWithTime<Meta>
                get() = valueFlow.value

            override fun subscribeWithTime(): Flow<ValueWithTime<Meta>>  = valueFlow

            override fun toString(): String = "ValueState.fromDeviceMessageSource($deviceName, $propertyName)"

        }
    }

    override val descriptor: MetaDescriptor? = null

}
