package space.kscience.simulation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Suspend the collection of this [Flow] until event time is lower that threshold
 */
public fun <E : WithTime> Flow<E>.withTimeThreshold(
    threshold: Flow<Instant>
): Flow<E> = transform { event ->
    threshold.first { it > event.time }
    emit(event)
}

/**
 * Generates events lazily from [origin], restarting the generator when [interrupt] changes its origin.
 *
 * @param lookaheadInterval optional generation ahead of observed time; active requests may extend this limit.
 * @param bufferSize maximum retained events, or [Channel.UNLIMITED]. Zero uses rendezvous delivery.
 */
public class GeneratingTimeline<E : Any>(
    origin: E,
    lookaheadInterval: Duration,
    timeOf: E.() -> Instant,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    bufferSize: Int = Channel.UNLIMITED,
    private val generator: suspend TimelineCollector<E>.(E) -> Unit
) : ProducerTimeline<E>(timeOf(origin), timeOf, coroutineContext, bufferSize) {

    init {
        state.initializeOrigin(origin, lookaheadInterval)
    }

    override fun events(): Flow<E> = flow {
        val origin = state.origin()
        val timelineCollector = object : TimelineCollector<E> {
            override val time: StateFlow<Instant> get() = this@GeneratingTimeline.time
            override var lastEvent: E? = origin

            override suspend fun emit(value: E) {
                this@flow.emit(value)
                lastEvent = value
            }
        }
        timelineCollector.generator(origin)
    }

    /**
     * Replace unobserved future events without changing already delivered events or completed request ranges.
     */
    public suspend fun interrupt(newStart: E) {
        state.restart(newStart)
    }
}
