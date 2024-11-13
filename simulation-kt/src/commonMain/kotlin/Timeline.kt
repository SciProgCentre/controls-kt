package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant


public interface TimelineEvent {
    public val time: Instant
}

public interface TimelineObserver: AutoCloseable {
    /**
     * The time of the last event collected by this collector
     */
    public val lastCollectedEventTime: Instant

    /**
     * Collect all uncollected events from [lastCollectedEventTime] to [upTo].
     *
     * By default, collects all events.
     */
    public suspend fun collect(upTo: Instant = Instant.DISTANT_FUTURE)
}

/**
 * A time-ordered sequence of events of type [E]. There time of events is strictly monotonic, meaning that the time of
 * the next event is greater than the previous event time.
 *
 * Timeline guarantees that all collectors could read all events when they need. Meaning that all unread events are cached.
 *
 * Timeline guarantees that already read events won't change, but unread events could change.
 */
public interface Timeline<E : TimelineEvent> {
    /**
     * The timestamp of the last event in a timeline
     */
    public val lastEventTime: Instant?

    /**
     * The time of the last event that was observed by all observers
     */
    public val observedTime: Instant

    /**
     * Flow events from [observedTime] to [lastEventTime].
     *
     * The resulting flow is finite and should not suspend.
     *
     * This method does not affect [observedTime].
     */
    public fun flowUnobservedEvents(): Flow<E>

    /**
     * Attach observer to this [Timeline]. The observer collection is not triggered right away, but only on demand.
     *
     * Each collection shifts [TimelineObserver.lastCollectedEventTime] for this observer.
     * The value of [observedTime] is the least of all observers [TimelineObserver.lastCollectedEventTime].
     */
    public suspend fun observe(
        collector: suspend Flow<E>.() -> Unit
    ): TimelineObserver

    /**
     * Advance simulation time to [toTime]. This method forces all observers to collect all events in the given range.
     *
     * This method suspends until all advancement is done
     */
    public suspend fun advance(toTime: Instant)

    /**
     * Interrupt generation of this timeline and discard unconsumed events after [atTime].
     *
     * Throw exception if at least one observer advanced
     */
    public suspend fun interrupt(atTime: Instant): Unit
}