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
public class SharedTimeline<E : TimelineEvent>(
    startTime: Instant,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : AbstractTimeline<E>(startTime, coroutineContext) {

    private val events = MutableSharedFlow<E>(replay = Channel.UNLIMITED)

    override fun events(): Flow<E> = events

    /**
     * Emit new event to the timeline
     */
    public suspend fun emit(event: E) {
        if (event.time < (events.replayCache.lastOrNull()?.time ?: time.value)) {
            error("Can't emit event $event because timeline monotony is broken")
        }
        events.emit(event)
    }
}