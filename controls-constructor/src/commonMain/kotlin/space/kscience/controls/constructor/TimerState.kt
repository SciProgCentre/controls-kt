package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import space.kscience.controls.time.ClockManager
import kotlin.time.Duration

/**
 * A dedicated [DeviceState] that operates with time.
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
) : DeviceState<Instant> {

    private val clock = MutableStateFlow(initialValue)

    private val updateJob = clockManager.context.launch(clockManager.asDispatcher()) {
        while (isActive) {
            clock.value = clockManager.clock.now()
            delay(tick)
        }
    }

    override val valueFlow: Flow<Instant> get() = clock

    override val value: Instant get() = clock.value

    override fun toString(): String = "TimerState(tick=$tick)"
}