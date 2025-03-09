package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import space.kscience.controls.api.Device
import space.kscience.controls.manager.getCoroutineDispatcher
import kotlin.time.Duration

/**
 * Do a recurring (with a fixed delay) task on a device.
 */
public fun <D : Device> D.doRecurring(
    interval: Duration,
    debugTaskName: String? = null,
    task: suspend D.() -> Unit,
): Job {
    val taskName = debugTaskName ?: "task[${task.hashCode().toString(16)}]"
    val dispatcher = getCoroutineDispatcher()
    return launch(CoroutineName(taskName) + dispatcher) {
        while (isActive) {
            delay(interval)
            //launch in parent scope to properly evaluate exceptions
            this@doRecurring.launch(CoroutineName("$taskName-recurring") + dispatcher) {
                task()
            }
        }
    }
}

/**
 * Perform a recurring asynchronous read action and return a flow of results.
 * The flow is lazy, so action is not performed unless flow is consumed.
 * The flow uses caller context. To call it on device context, use `flowOn(coroutineContext)`.
 *
 * The flow is canceled when the device scope is canceled
 */
public fun <D : Device, R> D.readRecurring(
    interval: Duration,
    debugTaskName: String? = null,
    reader: suspend D.() -> R,
): Flow<R> = flow {
    doRecurring(interval, debugTaskName) {
        emit(reader())
    }
}