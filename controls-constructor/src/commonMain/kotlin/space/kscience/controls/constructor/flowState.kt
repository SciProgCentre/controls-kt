package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


private class StateFlowAsState<T>(
    val flow: MutableStateFlow<T>,
) : MutableDeviceState<T> {
    override var value: T by flow::value
    override val valueFlow: Flow<T> get() = flow

    override fun toString(): String = "FlowAsState($value)"
}

/**
 * Create a read-only [DeviceState] that wraps [MutableStateFlow].
 * No data copy is performed.
 */
public fun <T> MutableStateFlow<T>.asDeviceState(): MutableDeviceState<T> = StateFlowAsState(this)