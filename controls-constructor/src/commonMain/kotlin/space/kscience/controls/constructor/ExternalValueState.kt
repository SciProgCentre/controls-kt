package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clockManager
import space.kscience.dataforge.context.Context
import kotlin.time.Duration


private open class ExternalValueState<T>(
    val scope: CoroutineScope,
    initialValue: T,
    val timer: TimerState,
    val reader: suspend () -> T,
) : ValueState<T> {

    protected val flow: StateFlow<ValueWithTime<T>> = timer.subscribe().map { now ->
        ValueWithTime(reader(), now)
    }.stateIn(scope, SharingStarted.Eagerly, ValueWithTime(initialValue, timer.now()))

    override val valueWithTime: ValueWithTime<T> get() = flow.value

    override fun subscribeWithTime(): StateFlow<ValueWithTime<T>> = flow

    override fun toString(): String = "ExternalState(value=$value)"
}

/**
 * Create a [ValueState] which is constructed by regularly reading external value
 */
public fun <T> ValueState.Companion.external(
    context: Context,
    readInterval: Duration,
    initialValue: T,
    scope: CoroutineScope = context,
    reader: suspend () -> T,
): ValueState<T> = ExternalValueState(scope, initialValue, TimerState(context.clockManager, readInterval), reader)

private class MutableExternalValueState<T>(
    context: Context,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
    val writer: suspend (T) -> Unit,
    scope: CoroutineScope = context,
) : ExternalValueState<T>(
    scope = scope,
    initialValue = initialValue,
    timer = TimerState(context.clockManager, readInterval),
    reader = reader
), MutableValueState<T> {

    override var value: T
        get() = super.value
        set(value) {
            scope.launch {
                writer(value)
            }
        }

    override suspend fun emit(value: T) {
        withContext(scope.coroutineContext.minusKey(Job)) {
            writer(value)
        }
    }
}

/**
 * Create a [MutableValueState] which is constructed by regularly reading external value and allows writing
 */
public fun <T> ValueState.Companion.external(
    context: Context,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
    writer: suspend (T) -> Unit,
    scope: CoroutineScope = context,
): MutableValueState<T> = MutableExternalValueState(context, readInterval, initialValue, reader, writer, scope)