package space.kscience.simulation

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Instant

public interface TimelineCollector<E : Any> : FlowCollector<E> {
    public val time: StateFlow<Instant>
    public val lastEvent: E?
}

public interface TimelineInterval : WithTime {
    public val startTime: Instant
    public val duration: Duration

    override val time: Instant get() = startTime + duration
}

public data class TimelineEvent<T>(override val time: Instant, val value: T) : WithTime