package space.kscience.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Suspend the collection of this [Flow] until event time is lower that threshold
 */
public fun <E : TimelineEvent> Flow<E>.withTimeThreshold(
    threshold: Flow<Instant>
): Flow<E> = transform { event ->
    threshold.first { it > event.time }
    emit(event)
}

/**
 * @param lookaheadInterval an interval for generated events ahead of the last observed event.
 */
public class GeneratingTimeline<E : TimelineEvent>(
    private val generationScope: CoroutineScope,
    private val origin: E,
    private val lookaheadInterval: Duration,
    private val generator: suspend FlowCollector<E>.(E) -> Unit
) : AbstractTimeline<E>(generationScope, origin.time) {

    private val startEventFlow = MutableStateFlow(origin)

    private data class EventWithOrigin<E : TimelineEvent>(val origin: E, val event: E) : TimelineEvent {
        override val time: Instant get() = event.time
    }

    private val events: SharedFlow<E> = flow {
        coroutineScope {
            startEventFlow.collect { startEvent ->
                emitAll(
                    flow { generator(startEvent) }.takeWhile { startEvent == startEventFlow.value }.map {
                        EventWithOrigin(startEvent, it)
                    }
                )
            }
        }
    }.withTimeThreshold(
        threshold = time.map { it + lookaheadInterval }
    ).buffer(Channel.UNLIMITED).mapNotNull {
        //it.event
        it.takeIf { it.origin == startEventFlow.value }?.event
    }.shareIn(
        scope = generationScope,
        started = SharingStarted.Eagerly,
    )

    override fun events(): Flow<E> = events

    public suspend fun interrupt(newStart: E) {
        check(newStart.time >= time.value) {
            "Can't interrupt generating timeline after observed event"
        }
        startTime = newStart.time
        startEventFlow.emit(newStart)
    }
}