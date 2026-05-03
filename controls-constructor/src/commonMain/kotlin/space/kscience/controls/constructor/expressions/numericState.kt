package space.kscience.controls.constructor.expressions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateWithDependencies
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Integrates the values within a specified time window.
 *
 * @param window The duration of the time window over which to calculate the integral.
 * @param scope The coroutine scope in which the integration logic should execute.
 * @return A [ValueState] representing the integrated result as a time-coupled value.
 */
public fun ValueState<ValueWithTime<Double>>.integrate(
    window: Duration,
    scope: CoroutineScope,
): ValueState<ValueWithTime<Double>> = object : ValueStateWithDependencies<ValueWithTime<Double>> {
    private val history = mutableListOf<ValueWithTime<Double>>()
    private val state = MutableStateFlow(value)

    private val job = this@integrate.subscribe().onEach { value ->
        history.add(value)
        history.removeAll { it.time < (value.time - window) }
        state.emit(ValueWithTime(history.sumOf { it.value }, value.time))
    }.launchIn(scope)

    override val dependencies = listOf(this)

    override val value: ValueWithTime<Double> get() = state.value

    override fun subscribe(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.integrate(state=${state.value}, window=$window)"
}

/**
 * Computes the derivative of the current `ValueState` by calculating the rate of change
 * of the encapsulated `ValueWithTime<Double>` value with respect to time.
 *
 * The returned [ValueState] emits the derivative values continuously based on the
 * changes in the original `ValueState`.
 *
 * @param scope The `CoroutineScope` in which the state operations and subscriptions
 *              are executed.
 * @return A `ValueState` containing the derivative of the initial state's
 *         `ValueWithTime<Double>` value.
 */
public fun ValueState<ValueWithTime<Double>>.differentiate(
    scope: CoroutineScope,
): ValueState<ValueWithTime<Double>> = object : ValueStateWithDependencies<ValueWithTime<Double>> {

    private var previous = value
    private val state = MutableStateFlow(ValueWithTime(0.0, value.time))

    private val job = this@differentiate.subscribe().onEach { value ->
        //skip invalid time marks
        if(value.time <= previous.time) return@onEach

        val diff = (value.value - previous.value) / (value.time - previous.time).toDouble(DurationUnit.SECONDS)
        state.emit(ValueWithTime(diff, value.time))
    }.launchIn(scope)

    override val dependencies: Collection<ValueState<*>> get() = listOf(this)

    override val value: ValueWithTime<Double>
        get() = state.value

    override fun subscribe(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.differentiate(state=${state.value})"

}

