package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Clock


private class StateFlowAsState<T>(
    val flow: MutableStateFlow<T>,
    val clock: Clock = Clock.System,
) : MutableValueState<T> {
    override var value: T by flow::value

    override val valueWithTime: ValueWithTime<T> get() = ValueWithTime(value, clock.now())

    override suspend fun emit(value: T) {
        flow.emit(value)
    }

    override fun subscribe(): StateFlow<T> = flow

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = flow.map { ValueWithTime(it, clock.now()) }

    override fun toString(): String = "FlowAsState($value)"
}

/**
 * Create a read-only [ValueState] that wraps [MutableStateFlow].
 * No data copy is performed.
 */
public fun <T> MutableStateFlow<T>.asMutableValueState(): MutableValueState<T> = StateFlowAsState(this)