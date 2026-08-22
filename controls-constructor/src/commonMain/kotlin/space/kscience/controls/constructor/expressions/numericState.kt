package space.kscience.controls.constructor.expressions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateWithDependencies
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 *
 */
public fun <T : Any> ValueWithTime<T?>.withDefault(default: T): ValueWithTime<T> {
    return ValueWithTime(value ?: default, time)
}

/**
 * Integrates the values within a specified time window.
 *
 * @param window The duration of the time window over which to calculate the integral.
 * @param scope The coroutine scope in which the integration logic should execute.
 * @return A [ValueState] representing the integrated result as a time-coupled value.
 */
public fun ValueState<Double?>.integrate(
    window: Duration,
    scope: CoroutineScope,
    startingValue: ValueWithTime<Double> = valueWithTime.withDefault(0.0)
): ValueState<Double> = object : ValueStateWithDependencies<Double> {
    private val history: MutableList<ValueWithTime<Double>> = mutableListOf(startingValue)
    private val state: MutableStateFlow<ValueWithTime<Double>> = MutableStateFlow(startingValue)
    private val mutex = Mutex()

    private val job = this@integrate.subscribeWithTime().onEach { (value, time) ->
        mutex.withLock {
            if (value != null && time > state.value.time) {
                history.add(ValueWithTime(value, time))
            }
            history.removeAll { it.time < (time - window) }
            state.emit(ValueWithTime(history.sumOf { it.value }, time))
        }
    }.launchIn(scope)

    override val dependencies = listOf(this@integrate)

    override val valueWithTime: ValueWithTime<Double> get() = state.value

    override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.integrate(state=${state.value}, window=$window)"
}

/**
 * Computes the derivative of the current `ValueState` by calculating the rate of change
 * of the encapsulated `ValueWithTime<Double>` value with respect to time.
 *
 * The returned [ValueState] emits the derivative values continuously based on the
 * changes in the original `ValueState`.
 */
public fun ValueState<Double>.differentiate(
    scope: CoroutineScope,
): ValueState<Double> = object : ValueStateWithDependencies<Double> {

    private var previous = valueWithTime
    private val state = MutableStateFlow(ValueWithTime(0.0, time))

    private val job = this@differentiate.subscribeWithTime().onEach { value ->
        //skip invalid time marks
        if (value.time <= previous.time) return@onEach

        val diff = (value.value - previous.value) / (value.time - previous.time).toDouble(DurationUnit.SECONDS)
        state.emit(ValueWithTime(diff, value.time))
        previous = value
    }.launchIn(scope)

    override val dependencies: Collection<ValueState<*>> get() = listOf(this)

    override val valueWithTime: ValueWithTime<Double> get() = state.value

    override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.differentiate(state=${state.value})"

}

