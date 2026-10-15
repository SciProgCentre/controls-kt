package space.kscience.controls.expressions

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
import kotlin.time.Instant

/**
 * Returns a new [ValueWithTime] with the given [default] value if the original value is null.
 */
public fun <T : Any> ValueWithTime<T?>.withDefault(default: T): ValueWithTime<T> = ValueWithTime(value ?: default, time)

/**
 * Calculates a rolling time trapezoid integral, using the source value at construction as the default [startingValue].
 * Adds a sample only when its time is later than the current result's time. Every event, including
 * null samples, trims the [window] and republishes the result with its own time. Out of order samples are ignored.
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
        //out of order samples are ignored
        if (time <= state.value.time) return@onEach

        mutex.withLock {
            if (value != null) {
                history.add(ValueWithTime(value, time))
            }
            history.removeAll { it.time < (time - window) }

            var integral = 0.0
            if (history.isNotEmpty()) {
                var previous = history.first()

                if (history.size == 1) {
                    integral = previous.value
                } else {
                    for (i in 1 until history.size) {
                        val current = history[i]
                        val dt = (current.time - previous.time).toDouble(DurationUnit.SECONDS)
                        integral += (previous.value + current.value) / 2.0 * dt
                        previous = current
                    }
                }
            }

            state.emit(ValueWithTime(integral, time))
        }
    }.launchIn(scope)

    override val dependencies = listOf(this@integrate)

    override val valueWithTime: ValueWithTime<Double> get() = state.value

    override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.integrate(state=${state.value}, window=$window)"
}

/**
 * Sum observed values in a time window.
 * Samples older than the current result are ignored; equal timestamps are accepted.
 * Accepted null samples add nothing but advance the window and result time.
 *
 * [startingValue] is a separate initial sample, zero by default.
 * The first source sample is taken from the subscription.
 */
public fun ValueState<Double?>.accumulate(
    window: Duration,
    scope: CoroutineScope,
    startingValue: ValueWithTime<Double> = ValueWithTime(0.0, Instant.DISTANT_PAST)
): ValueState<Double> = object : ValueStateWithDependencies<Double> {
    private val history: MutableList<ValueWithTime<Double>> = mutableListOf(startingValue)
    private val state: MutableStateFlow<ValueWithTime<Double>> = MutableStateFlow(startingValue)
    private val mutex = Mutex()

    private val job = this@accumulate.subscribeWithTime().onEach { (value, time) ->
        mutex.withLock {
            if (time < state.value.time) return@withLock
            if (value != null) {
                history.add(ValueWithTime(value, time))
            }
            history.removeAll { it.time < (time - window) }

            state.emit(ValueWithTime(history.sumOf { it.value }, time))
        }
    }.launchIn(scope)

    override val dependencies = listOf(this@accumulate)

    override val valueWithTime: ValueWithTime<Double> get() = state.value

    override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.accumulate(state=${state.value}, window=$window)"
}

/**
 * Calculates the rate of change from strictly increasing timed samples. Null samples leave the baseline unchanged.
 * An initial value without a time mark is not a baseline; the first timed sample then sets it without a derivative.
 */
public fun ValueState<Double?>.differentiate(
    scope: CoroutineScope,
): ValueState<Double> = object : ValueStateWithDependencies<Double> {

    private var previous: ValueWithTime<Double?> = this@differentiate.valueWithTime
        .takeIf { it.time != Instant.DISTANT_PAST } ?: ValueWithTime(null, Instant.DISTANT_PAST)
    private val state = MutableStateFlow(ValueWithTime(0.0, previous.time))

    private val job = this@differentiate.subscribeWithTime().onEach { (value, time) ->
        if (value == null) return@onEach
        //skip invalid time marks
        if (time <= previous.time) return@onEach

        previous.value?.let { previousValue ->
            val diff = (value - previousValue) / (time - previous.time).toDouble(DurationUnit.SECONDS)
            state.emit(ValueWithTime(diff, time))
        }
        previous = ValueWithTime(value, time)
    }.launchIn(scope)

    override val dependencies: Collection<ValueState<*>> get() = listOf(this@differentiate)

    override val valueWithTime: ValueWithTime<Double> get() = state.value

    override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = state

    override fun toString(): String = "DeviceState.differentiate(state=${state.value})"

}

