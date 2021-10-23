package ru.mipt.npm.controls.spec

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Perform a recurring asynchronous read action and return a flow of results.
 * The flow is lazy so action is not performed unless flow is consumed.
 * The flow uses called context. In order to call it on device context, use `flowOn(coroutineContext)`.
 *
 * The flow is canceled when the device scope is canceled
 */
public fun <D : DeviceBase<D>, R> D.readRecurring(interval: Duration, reader: suspend D.() -> R): Flow<R> = flow {
    while (isActive) {
        kotlinx.coroutines.delay(interval)
        emit(reader())
    }
}

/**
 * Do a recurring task on a device. The task could
 */
public fun <D : DeviceBase<D>> D.doRecurring(interval: Duration, task: suspend D.() -> Unit): Job = launch {
    while (isActive) {
        kotlinx.coroutines.delay(interval)
        task()
    }
}