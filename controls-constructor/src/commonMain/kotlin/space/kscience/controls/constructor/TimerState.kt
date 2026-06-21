package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A dedicated [ValueState] that operates with time.
 * The state changes with [tick] interval and always shows the time of the last update.
 *
 * Both [tick] and current time are computed by [clockManager] enabling time manipulation.
 *
 * The timer runs indefinitely until the parent context is closed
 */
public class TimerState(
    public val clockManager: ClockManager,
    public val tick: Duration,
    initialValue: Instant = Instant.DISTANT_PAST,
) : ValueState<Instant>, Clock {

    private val timeFlow = MutableStateFlow(initialValue)

    private val updateJob = clockManager.context.launch(clockManager.simulationDispatcher) {
        while (isActive) {
            timeFlow.emit(clockManager.clock.now())
            delay(tick)
        }
    }

    override fun subscribe(): StateFlow<Instant> = timeFlow

    override fun subscribeWithTime(): Flow<ValueWithTime<Instant>> = timeFlow.map { ValueWithTime(it,it) }

    override val value: Instant get() = timeFlow.value

    override fun now(): Instant =value

    override val valueWithTime: ValueWithTime<Instant> get() = ValueWithTime(value, value)

    override fun toString(): String = "TimerState(time=${timeFlow.value}, tick=$tick)"
}
