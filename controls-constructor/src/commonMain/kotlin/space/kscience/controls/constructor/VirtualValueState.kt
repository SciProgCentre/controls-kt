package space.kscience.controls.constructor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import space.kscience.controls.time.ValueWithTime
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.ContextAware
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A [MutableValueState] that does not correspond to a physical state
 *
 */
private class VirtualValueState<T>(
    initialValue: T,
    val clock: Clock = Clock.System,
) : MutableValueState<T> {

    private val flow = MutableStateFlow(ValueWithTime(initialValue, clock.now()))

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = flow

    override var valueWithTime: ValueWithTime<T>
        get() = flow.value
        set(value) {
            flow.value = value
        }

    override var value: T
        get() = flow.value.value
        set(value) {
            flow.value = ValueWithTime(value, clock.now())
        }

    override suspend fun emit(value: T) {
        flow.emit(ValueWithTime(value, clock.now()))
    }

    override fun toString(): String = "ValueState.Virtual($value)"
}


/**
 * A [MutableValueState] that does not correspond to a physical state
 *
 */
public fun <T> MutableValueState(
    initialValue: T,
    clock: Clock = Clock.System,
): MutableValueState<T> = VirtualValueState(initialValue, clock)

/**
 * A [MutableValueState] that does not correspond to a physical state
 *
 * Inherits context clock
 */
public fun <T> ContextAware.MutableValueState(
    initialValue: T,
): MutableValueState<T> = VirtualValueState(initialValue, context.clock)


/**
 * Create a [ValueState] with constant value
 */
public fun <T> ValueState(
    value: T,
    time: Instant = Instant.DISTANT_PAST,
): ValueState<T> = object : ValueState<T> {
    override val value: T get() = value

    override val time: Instant = time

    override val valueWithTime: ValueWithTime<T> get() = ValueWithTime(value, time)

    override fun subscribe(): Flow<T> = flowOf(value)

    override fun subscribeWithTime(): Flow<ValueWithTime<T>> = flowOf(valueWithTime)

    override fun toString(): String = "ValueState.Const($value)"

}