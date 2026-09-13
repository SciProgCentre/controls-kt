package space.kscience.simulation

import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Instant

public interface CollectingTimeline<E : Any> : Timeline<E>, TimelineCollector<E>

/**
 * A manually populated [Timeline]. Accepted events remain available to registered observers until consumed.
 *
 * @param bufferSize maximum retained events, or [Channel.UNLIMITED]. Emit suspends when the buffer is full.
 * Zero uses rendezvous delivery and waits for observers without discarding events.
 */
public class SharedTimeline<E : Any>(
    startTime: Instant,
    timeOf: E.() -> Instant,
    bufferSize: Int = Channel.UNLIMITED,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : ProducerTimeline<E>(startTime, timeOf, coroutineContext, bufferSize), CollectingTimeline<E> {

    override val lastEvent: E? get() = state.lastEvent

    /**
     * Accept an event in nondecreasing time order, waiting for capacity when necessary.
     */
    override suspend fun emit(value: E) {
        state.publish(value)
    }

    /**
     * Declare that no more events at or before [upTo] will be published. Earlier declarations are harmless.
     * Already accepted events remain readable; later emissions at or before this boundary are rejected.
     */
    public suspend fun completeThrough(upTo: Instant) {
        state.completeThrough(upTo)
    }

    /**
     * End publication while allowing observers to drain accepted events. Repeated calls are harmless.
     */
    public suspend fun finish() {
        state.finish()
    }
}
