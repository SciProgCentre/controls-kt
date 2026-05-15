package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Clock

/**
 * Create a read-only [ValueState] that wraps [MutableStateFlow].
 * No data copy is performed.
 */
public fun <T> StateFlow<T>.asValueState(
    clock: Clock
): ValueState<T> = object : ValueState<T> {
    override val value: T by this@asValueState::value

    override val valueWithTime: ValueWithTime<T> get() = ValueWithTime(value, clock.now())

    override fun subscribe(): StateFlow<T> = this@asValueState

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = this@asValueState.map { ValueWithTime(it, clock.now()) }

    override fun toString(): String = "ValueState.fromStateFlow($value)"
}


/**
 * Create a [MutableValueState] that wraps [MutableStateFlow].
 * No data copy is performed.
 */
public fun <T> MutableStateFlow<T>.asMutableValueState(
    clock: Clock
): MutableValueState<T> = object : MutableValueState<T> {
    override var value: T by this@asMutableValueState::value

    override val valueWithTime: ValueWithTime<T> get() = ValueWithTime(value, clock.now())

    override suspend fun emit(value: T) {
        this@asMutableValueState.emit(value)
    }

    override fun subscribe(): StateFlow<T> = this@asMutableValueState

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> =
        this@asMutableValueState.map { ValueWithTime(it, clock.now()) }

    override fun toString(): String = "ValueState.fromMutableStateFlow($value)"
}