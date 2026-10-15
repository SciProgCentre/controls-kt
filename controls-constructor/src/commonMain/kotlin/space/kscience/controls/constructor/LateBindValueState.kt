package space.kscience.controls.constructor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Instant

/**
 * A device state implementation that supports deferred binding to another [ValueState] instance.
 * This allows for dynamic assignment of the state later during the program execution.
 *
 * @param T The type of the state value.
 * @property initialValue The initial value of the device state before it is bound to another state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class LateBindValueState<T>(
    private val initialValue: T,
    private val initialTime: Instant = Instant.DISTANT_PAST,
) : ValueState<T> {

    private var binding = CompletableDeferred<ValueState<T>>()

    public fun bind(state: ValueState<T>) {
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

    override val valueWithTime: ValueWithTime<T>
        get() = if (isBound) {
            binding.getCompleted().valueWithTime
        } else {
            ValueWithTime(initialValue, initialTime)
        }

    override fun subscribe(): Flow<T> = if (isBound) {
        binding.getCompleted().subscribe()
    } else {
        flow {
            emit(initialValue)
            binding.await().subscribe().collect {
                emit(it)
            }
        }
    }

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = if (isBound) {
        binding.getCompleted().subscribeWithTime()
    } else {
        flow {
            emit(ValueWithTime(initialValue, initialTime))
            binding.await().subscribeWithTime().collect {
                emit(it)
            }
        }
    }

    override fun toString(): String = if (isBound) {
        "LateBindDeviceState(binding=${binding.getCompleted()})"
    } else {
        "LateBindDeviceState(initialValue=$initialValue)"
    }
}