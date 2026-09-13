package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A handler for observation of a timeline. On close stops collection.
 */
public interface TimelineObserver : AutoCloseable {
    /**
     * The subjective time of this observer (last observed time)
     */
    public val time: StateFlow<Instant>

    /**
     * Collect unread events through [upTo], including events with equal timestamps.
     * Successful completion protects the whole interval against subsequent changes.
     * Cancellation retains already delivered events without completing the remaining interval.
     * Cancelling this request does not cancel a collector callback that has already started.
     * On return or cancellation, [time] reflects all events already handed to the collector.
     */
    public suspend fun collect(upTo: Instant)
}

/**
 * Collect events for a fixed [duration] since last observed time
 */
public suspend fun TimelineObserver.collect(duration: Duration): Unit = collect(time.value + duration)

/**
 * A sequence of events of type [E] in nondecreasing time order.
 *
 * Unread events remain available to registered observers; a slow observer may suspend a bounded producer.
 *
 * Delivered events and successfully completed intervals cannot change. Unobserved future events may change.
 */
public interface Timeline<E : Any> {
    /**
     * A subjective time of this timeline. The subjective time is the last observed time.
     */
    public val time: StateFlow<Instant>

    /** Return a stable event timestamp without changing timeline state. */
    public fun timeOf(event: E): Instant

    /**
     * Attach an observer after the prefix already delivered to other observers.
     *
     * [TimelineObserver.time] advances on delivery, not on a request over an empty interval.
     * The collector runs outside the timeline lock. Its completion or failure closes the observer.
     */
    public suspend fun observe(
        collector: suspend Flow<E>.() -> Unit
    ): TimelineObserver

    /**
     * Advance simulation time to [toTime]. This method forces all observers to collect all events in the given range.
     *
     * Requests the observers registered at entry concurrently. Failure cancels the other requests,
     * not their registrations. With no observers this is a no-op.
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
