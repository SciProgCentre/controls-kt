package space.kscience.simulation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A manually mutable [Timeline] that could be modified via [emit] method by multiple
 */
public class SharedTimeline<E : Any>(
    startTime: Instant,
    timeOf: E.() -> Instant,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : ProducerTimeline<E>(startTime, timeOf, coroutineContext), TimelineCollector<E> {

    private val events = MutableSharedFlow<E>(replay = Channel.UNLIMITED)

    override fun events(): Flow<E> = events

    override val lastEvent: E? get() = events.replayCache.lastOrNull()

    /**
     * Emit new event to the timeline
     */
    override suspend fun emit(event: E) {
        if (timeOf(event) < (events.replayCache.lastOrNull()?.let(::timeOf) ?: time.value)) {
            error("Can't emit event $event because timeline monotony is broken")
        }
        events.emit(event)
    }
}