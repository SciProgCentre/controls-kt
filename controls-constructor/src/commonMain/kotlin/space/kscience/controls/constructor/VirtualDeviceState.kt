package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [MutableDeviceState] that does not correspond to a physical state
 *
 * @param callback a synchronous callback that could be used without a scope
 */
private class VirtualDeviceState<T>(
    initialValue: T,
    private val callback: (T) -> Unit = {}
) : MutableDeviceState<T> {

    init {
        callback(initialValue)
    }

    private val flow = MutableStateFlow(initialValue)

    override fun subscribe(): StateFlow<T> = flow

    override var value: T
        get() = flow.value
        set(value) {
            flow.value = value
            callback(value)
        }

    override suspend fun emit(value: T) {
        flow.emit(value)
        callback(value)
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
    callback: (T) -> Unit = {}
): MutableDeviceState<T> = VirtualDeviceState(initialValue, callback)


/**
 * Create a [DeviceState] with constant value
 */
public fun <T> DeviceState(
    value: T
): DeviceState<T> = object : DeviceState<T> {
    override val value: T get() = value

    override fun subscribe(): Flow<T> = flowOf(value)

    override fun toString(): String = "ConstDeviceState($value)"

}