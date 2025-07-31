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


private open class ExternalDeviceState<T>(
    val scope: CoroutineScope,
    val readInterval: Duration,
    initialValue: T,
    val reader: suspend () -> T,
) : DeviceState<T> {

    protected val flow: StateFlow<T> = flow {
        while (true) {
            delay(readInterval)
            emit(reader())
        }
    }.stateIn(scope, SharingStarted.Eagerly, initialValue)

    override val value: T get() = flow.value

    override fun subscribe(): StateFlow<T> = flow

    override fun toString(): String = "ExternalState()"
}

/**
 * Create a [DeviceState] which is constructed by regularly reading external value
 */
public fun <T> DeviceState.Companion.external(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
): DeviceState<T> = ExternalDeviceState(scope, readInterval, initialValue, reader)

private class MutableExternalDeviceState<T>(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
    val writer: suspend (T) -> Unit,
) : ExternalDeviceState<T>(scope, readInterval, initialValue, reader), MutableDeviceState<T> {
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
 * Create a [MutableDeviceState] which is constructed by regularly reading external value and allows writing
 */
public fun <T> DeviceState.Companion.external(
    scope: CoroutineScope,
    readInterval: Duration,
    initialValue: T,
    reader: suspend () -> T,
    writer: suspend (T) -> Unit,
): MutableDeviceState<T> = MutableExternalDeviceState(scope, readInterval, initialValue, reader, writer)