package space.kscience.controls.constructor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Instant

/**
 * A state that can be bound later and explicitly rebound with [rebind].
 * Existing subscriptions follow the current binding.
 *
 * @param T The type of the state value.
 * @property initialValue The initial value of the device state before it is bound to another state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class LateBindValueState<T>(
    private val initialValue: T,
    private val initialTime: Instant = Instant.DISTANT_PAST,
) : ValueState<T> {

    private class Binding<T>(val state: ValueState<T>)

    private val binding = MutableStateFlow<Binding<T>?>(null)

    /** Bind once. Use [rebind] to replace an existing binding. */
    public fun bind(state: ValueState<T>) {
        check(binding.compareAndSet(null, Binding(state))) { "The state is already bound" }
    }

    /** Set or replace the binding and switch active subscriptions to [state]. */
    public fun rebind(state: ValueState<T>) {
        binding.value = Binding(state)
    }

    public val isBound: Boolean get() = binding.value != null

    override val value: T
        get() {
            val current = binding.value
            return if (current == null) initialValue else current.state.value
        }

    override val valueWithTime: ValueWithTime<T>
        get() = binding.value?.state?.valueWithTime ?: ValueWithTime(initialValue, initialTime)

    // Avoid buffering old-source samples across a binding change.
    override fun subscribe(): Flow<T> = binding.flatMapLatest {
        it?.state?.subscribe() ?: flowOf(initialValue)
    }.buffer(0)

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = binding.flatMapLatest {
        it?.state?.subscribeWithTime() ?: flowOf(ValueWithTime(initialValue, initialTime))
    }.buffer(0)

    override fun toString(): String = binding.value?.let {
        "LateBindDeviceState(binding=${it.state})"
    } ?: "LateBindDeviceState(initialValue=$initialValue)"
}