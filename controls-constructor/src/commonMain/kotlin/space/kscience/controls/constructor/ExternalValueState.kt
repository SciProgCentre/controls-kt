package space.kscience.controls.constructor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration


private open class ExternalValueState<T>(
    val scope: CoroutineScope,
    val readInterval: Duration,
    initialValue: T,
    val reader: suspend () -> T,
) : ValueState<T> {

    protected val flow: StateFlow<T> = flow {
        while (true) {
            delay(readInterval)
            emit(reader())
        }
    }.stateIn(scope, SharingStarted.Eagerly, initialValue)

    override val value: T get() = flow.value

    override fun subscribe(): StateFlow<T> = flow

    override fun toString(): String = "ExternalState(value=$value)"
}

/**
 * Create a [ValueState] which is constructed by regularly reading external value
 */
public fun <T> ValueState.Companion.external(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
): ValueState<T> = ExternalValueState(scope, readInterval, initialValue, reader)

private class MutableExternalValueState<T>(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
    val writer: suspend (T) -> Unit,
) : ExternalValueState<T>(scope, readInterval, initialValue, reader), MutableValueState<T> {
    override var value: T
        get() = super.value
        set(value) {
            scope.launch {
                writer(value)
            }
        }

    override suspend fun emit(value: T) {
        withContext(scope.coroutineContext) {
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
    reader: suspend () -> T,
    writer: suspend (T) -> Unit,
): MutableValueState<T> = MutableExternalValueState(scope, readInterval, initialValue, reader, writer)