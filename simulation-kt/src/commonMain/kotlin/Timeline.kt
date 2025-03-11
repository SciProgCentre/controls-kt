package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * A handler for observation of a timeline. On close stops collection.
 */
public interface TimelineObserver : AutoCloseable {
    /**
     * The subjective time of this observer (last observed time)
     */
    public val time: StateFlow<Instant>

    /**
     * Collect all uncollected events from [time] to [upTo]. Suspends until all valid events are collected.
     *
     */
    public suspend fun collect(upTo: Instant)
}

/**
 * Collect events for a fixed [duration] since last observed time
 */
public suspend fun TimelineObserver.collect(duration: Duration): Unit = collect(time.value + duration)

/**
 * A time-ordered sequence of events of type [E]. There time of events is strictly monotonic, meaning that the time of
 * the next event is greater than the previous event time.
 *
 * Timeline guarantees that all collectors could read all events when they need. Meaning that all unread events are cached.
 *
 * Timeline guarantees that already read events won't change, but unread events could change.
 */
public interface Timeline<E : Any> {
    /**
     * A subjective time of this timeline. The subjective time is the last observed time.
     */
    public val time: StateFlow<Instant>

    public fun timeOf(event: E): Instant

    /**
     * Attach observer to this [Timeline]. The observer collection is triggered by timeline itself.
     *
     * Each collection shifts [TimelineObserver.time] for this observer.
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
}


/**
 * Perform [collector] action on each event
 */
public suspend fun <E : Any> Timeline<E>.observeEach(
    collector: suspend (E) -> Unit
): TimelineObserver = observe {
    collect(collector)
}