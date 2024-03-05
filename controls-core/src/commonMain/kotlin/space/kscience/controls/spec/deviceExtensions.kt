package space.kscience.controls.spec

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import kotlin.time.Duration

/**
 * Perform a recurring asynchronous read action and return a flow of results.
 * The flow is lazy, so action is not performed unless flow is consumed.
 * The flow uses caller context. To call it on device context, use `flowOn(coroutineContext)`.
 *
 * The flow is canceled when the device scope is canceled
 */
public fun <D : Device, R> D.readRecurring(interval: Duration, reader: suspend D.() -> R): Flow<R> = flow {
    while (isActive) {
        delay(interval)
        launch {
            emit(reader())
        }
    }
}

/**
 * Do a recurring (with a fixed delay) task on a device.
 */
public fun <D : Device> D.doRecurring(interval: Duration, task: suspend D.() -> Unit): Job = launch {
    while (isActive) {
        delay(interval)
        launch {
            task()
        }
    }
}