package space.kscience.simulation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Instant

public interface CollectingTimeline<E: Any>: Timeline<E>, TimelineCollector<E>

/**
 * A manually mutable [Timeline] that could be modified via [emit] method by multiple
 *
 * @param bufferSize the size of event buffer. If more than [bufferSize] events are emitted and not consumed via [observe], emitter suspends.
 */
public class SharedTimeline<E : Any>(
    startTime: Instant,
    timeOf: E.() -> Instant,
    bufferSize: Int = Channel.UNLIMITED,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : ProducerTimeline<E>(startTime, timeOf, coroutineContext), CollectingTimeline<E> {

    private val events = MutableSharedFlow<E>(replay = bufferSize)

    override fun events(): Flow<E> = events

    override val lastEvent: E? get() = events.replayCache.lastOrNull()

    /**
     * Emit new event to the timeline
     */
    override suspend fun emit(value: E) {
        if (timeOf(value) < (events.replayCache.lastOrNull()?.let(::timeOf) ?: time.value)) {
            error("Can't emit event $value because timeline monotony is broken")
        }
        events.emit(value)
    }
}