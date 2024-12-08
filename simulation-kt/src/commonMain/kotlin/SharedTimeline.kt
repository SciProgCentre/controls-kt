package space.kscience.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Instant

/**
 * A manually mutable [Timeline] that could be modified via [emit] method by multiple
 */
public class SharedTimeline<E : TimelineEvent>(
    timelineScope: CoroutineScope,
    startTime: Instant
) : AbstractTimeline<E>(timelineScope, startTime) {

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