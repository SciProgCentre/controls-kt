package space.kscience.controls.constructor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


private class StateFlowAsState<T>(
    val flow: MutableStateFlow<T>,
) : MutableDeviceState<T> {
    override var value: T by flow::value

    override suspend fun emit(value: T) {
        flow.emit(value)
    }

    override fun subscribe(): StateFlow<T> = flow

    override fun toString(): String = "FlowAsState($value)"
}

/**
 * Create a read-only [DeviceState] that wraps [MutableStateFlow].
 * No data copy is performed.
 */
public fun <T> MutableStateFlow<T>.asDeviceState(): MutableDeviceState<T> = StateFlowAsState(this)