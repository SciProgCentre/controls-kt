package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [MutableValueState] that does not correspond to a physical state
 *
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

    override fun toString(): String = "ValueState.Virtual($value)"
}


/**
 * A [MutableValueState] that does not correspond to a physical state
 *
 */
public fun <T> MutableValueState(
    initialValue: T,
): MutableValueState<T> = VirtualValueState(initialValue)


/**
 * Create a [ValueState] with constant value
 */
public fun <T> ValueState(
    value: T
): ValueState<T> = object : ValueState<T> {
    override val value: T get() = value

    override fun subscribe(): Flow<T> = flowOf(value)

    override fun toString(): String = "ValueState.Const($value)"

}