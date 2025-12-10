package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [MutableValueState] that does not correspond to a physical state
 *
 * @param callback a synchronous callback that could be used without a scope
 */
private class VirtualValueState<T>(
    initialValue: T,
) : MutableValueState<T> {

    private val flow = MutableStateFlow(initialValue)

    override fun subscribe(): StateFlow<T> = flow

    override var value: T
        get() = flow.value
        set(value) {
            flow.value = value
        }

    override suspend fun emit(value: T) {
        flow.emit(value)
    }

    override fun toString(): String = "VirtualDeviceState($value)"
}


/**
 * A [MutableDeviceState] that does not correspond to a physical state
 *
 * @param callback a synchronous callback that could be used without a scope
 */
public fun <T> MutableDeviceState(
    initialValue: T,
): MutableValueState<T> = VirtualValueState(initialValue)


/**
 * Create a [DeviceState] with constant value
 */
public fun <T> DeviceState(
    value: T
): ValueState<T> = object : ValueState<T> {
    override val value: T get() = value

    override fun subscribe(): Flow<T> = flowOf(value)

    override fun toString(): String = "ConstDeviceState($value)"

}