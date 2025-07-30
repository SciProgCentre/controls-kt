package space.kscience.controls.constructor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * A device state implementation that supports deferred binding to another [DeviceState] instance.
 * This allows for dynamic assignment of the state later during the program execution.
 *
 * @param T The type of the state value.
 * @property initialValue The initial value of the device state before it is bound to another state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class LateBindDeviceState<T>(
    scope: CoroutineScope,
    private val initialValue: T
) : DeviceState<T> {

    private var binding = CompletableDeferred<DeviceState<T>>()

    public fun bind(state: DeviceState<T>) {
        check(!binding.isCompleted) { "The state is already bound" }
        binding.complete(state)
    }

    public val isBound: Boolean get() = binding.isCompleted

    override val value: T
        get() = if (isBound) {
            binding.getCompleted().value
        } else {
            initialValue
        }

    override val valueFlow: StateFlow<T> = flow {
        val bound = binding.await()
        emit(bound.value)
        bound.valueFlow.collect {
            emit(it)
        }
    }.stateIn(scope, SharingStarted.Eagerly, initialValue)


    override fun toString(): String =
        "LateBindDeviceState(initialValue=$initialValue, binding=${binding.takeIf { it.isCompleted }})"
}