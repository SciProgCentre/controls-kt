package space.kscience.controls.constructor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Clock
import kotlin.time.Duration


private open class ExternalValueState<T>(
    val scope: CoroutineScope,
    val readInterval: Duration,
    initialValue: T,
    val clock: Clock = Clock.System,
    val reader: suspend () -> T,
) : ValueState<T> {

    protected val flow: StateFlow<ValueWithTime<T>> = flow {
        while (true) {
            delay(readInterval)
            emit(ValueWithTime(reader(), clock.now()))
        }
    }.stateIn(scope, SharingStarted.Eagerly, ValueWithTime(initialValue, clock.now()))

    override val valueWithTime: ValueWithTime<T> get() = flow.value

    override fun subscribeWithTime(): StateFlow<ValueWithTime<T>> = flow

    override fun toString(): String = "ExternalState(value=$value)"
}

/**
 * Create a [ValueState] which is constructed by regularly reading external value
 */
public fun <T> ValueState.Companion.external(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    clock: Clock = Clock.System,
    reader: suspend () -> T,
): ValueState<T> = ExternalValueState(scope, readInterval, initialValue, clock, reader)

private class MutableExternalValueState<T>(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    clock: Clock = Clock.System,
    reader: suspend () -> T,
    val writer: suspend (T) -> Unit,
) : ExternalValueState<T>(scope, readInterval, initialValue, clock, reader), MutableValueState<T> {

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
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    clock: Clock = Clock.System,
    reader: suspend () -> T,
    writer: suspend (T) -> Unit,
): MutableValueState<T> = MutableExternalValueState(scope, readInterval, initialValue, clock, reader, writer)