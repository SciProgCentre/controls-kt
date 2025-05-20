package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import space.kscience.controls.constructor.units.NumericalValue
import space.kscience.controls.constructor.units.UnitsOfMeasurement
import kotlin.reflect.KProperty

/**
 * An observable state of a device
 */
public interface DeviceState<out T> {
    public val value: T

    public val valueFlow: Flow<T>

    override fun toString(): String

    public companion object
}


public operator fun <T> DeviceState<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

/**
 * Use each value (including initial one) in a given [scope]
 */
public fun <T> DeviceState<T>.useValue(scope: CoroutineScope, block: suspend (T) -> Unit): Job =
    merge(flowOf(value), valueFlow).onEach(block).launchIn(scope)

/**
 * A mutable state of a device
 */
public interface MutableDeviceState<T> : DeviceState<T> {
    override var value: T
}

public operator fun <T> MutableDeviceState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

/**
 * Device state with a value that depends on other device states
 */
public interface DeviceStateWithDependencies<T> : DeviceState<T> {
    public val dependencies: Collection<DeviceState<*>>
}

public fun <T> DeviceState<T>.withDependencies(
    dependencies: Collection<DeviceState<*>>,
): DeviceStateWithDependencies<T> = object : DeviceStateWithDependencies<T>, DeviceState<T> by this {
    override val dependencies: Collection<DeviceState<*>> = dependencies
}

/**
 * Create a new read-only [DeviceState] that mirrors receiver state by mapping the value with [mapper].
 */
public fun <T, R> DeviceState.Companion.map(
    state: DeviceState<T>,
    mapper: (T) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state)

    override val value: R get() = mapper(state.value)

    override val valueFlow: Flow<R> = state.valueFlow.map(mapper)

    override fun toString(): String = "DeviceState.map(state=${state})"
}

public fun <T, R> DeviceState<T>.map(mapper: (T) -> R): DeviceStateWithDependencies<R> = DeviceState.map(this, mapper)

public fun DeviceState<NumericalValue<out UnitsOfMeasurement>>.values(): DeviceState<Double> =
    object : DeviceState<Double> {
        override val value: Double
            get() = this@values.value.value

        override val valueFlow: Flow<Double>
            get() = this@values.valueFlow.map { it.value }

        override fun toString(): String = this@values.toString()
    }

/**
 * Combine two device states into one read-only [DeviceState]. Only the latest value of each state is used.
 */
public fun <T1, T2, R> DeviceState.Companion.combine(
    state1: DeviceState<T1>,
    state2: DeviceState<T2>,
    mapper: (T1, T2) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = listOf(state1, state2)

    override val value: R get() = mapper(state1.value, state2.value)

    override val valueFlow: Flow<R> = kotlinx.coroutines.flow.combine(state1.valueFlow, state2.valueFlow, mapper)

    override fun toString(): String = "DeviceState.combine(state1=$state1, state2=$state2)"
}

/**
 * Combines multiple [DeviceState] instances into a single [DeviceStateWithDependencies].
 * The combined state value is derived by applying the provided [mapper] function to the collection of individual state values.
 *
 * @param T the type of the individual state values.
 * @param R the type of the combined state value.
 * @param states a collection of [DeviceState] instances whose values are to be combined.
 * @param mapper a function that takes a collection of state values and maps it to a combined value.
 * @return a [DeviceStateWithDependencies] representing the combined state, which has dependencies on the input [states].
 */
public inline fun <reified T, R> DeviceState.Companion.combine(
    states: Collection<DeviceState<T>>,
    crossinline mapper: (Array<T>) -> R,
): DeviceStateWithDependencies<R> = object : DeviceStateWithDependencies<R> {
    override val dependencies = states

    override val value: R get() = mapper(states.map { it.value }.toTypedArray())

    override val valueFlow: Flow<R> = kotlinx.coroutines.flow.combine<T, R>(states.map { it.valueFlow }, mapper)

    override fun toString(): String = "DeviceState.combine(states=${states.joinToString()})"
}