package space.kscience.simulation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
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

private class OriginChangedException : CancellationException("Origin is changed")

/**
 * @param lookaheadInterval an interval for generated events ahead of the last observed event.
 */
public class GeneratingTimeline<E : Any>(
    origin: E,
    private val lookaheadInterval: Duration,
    timeOf: E.() -> Instant,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val generator: suspend TimelineCollector<E>.(E) -> Unit
) : ProducerTimeline<E>(timeOf(origin), timeOf, coroutineContext) {

    private val startEventFlow = MutableStateFlow(origin)

    private inner class EventWithOrigin(val origin: E, val event: E) : WithTime {
        override val time: Instant get() = timeOf(event)
    }

    private val events: SharedFlow<E> = flow<EventWithOrigin> {
        coroutineScope {
            startEventFlow.collect { startEvent ->
                val timelineCollector = object : TimelineCollector<E> {
                    override val time: StateFlow<Instant> get() = this@GeneratingTimeline.time
                    override var lastEvent: E? = startEvent

                    override suspend fun emit(value: E) {
                        if (startEvent == startEvent) {
                            lastEvent = value
                            emit(EventWithOrigin(startEvent, value))
                        } else {
                            throw OriginChangedException()
                        }
                    }
                }

                try {
                    timelineCollector.generator(startEvent)
                } catch (_: OriginChangedException) {
                    return@collect
                }

//                emitAll(
//                    discrete innerFlow@{
//                        object : TimelineCollector<E> {
//                            override val time: StateFlow<Instant> get() = this@GeneratingTimeline.time
//                            override val lastEvent: E?
//                                get() = TODO("Not yet implemented")
//
//                            override suspend fun emit(value: E) {
//                                this@innerFlow.emit(value)
//                            }
//
//                        }.generator(startEvent)
//                    }.takeWhile {
//                        startEvent == startEventFlow.value
//                    }.map {
//                        EventWithOrigin(startEvent, it)
//                    }
//                )
            }
        }
    }.withTimeThreshold(
        threshold = time.map { it + lookaheadInterval }
    ).buffer(Channel.UNLIMITED).mapNotNull { event: GeneratingTimeline<E>.EventWithOrigin ->
        //a barrier to avoid leaking stale events after interruption from buffer
        event.takeIf { it.origin == startEventFlow.value }?.event
    }.shareIn(
        scope = timelineScope,
        started = SharingStarted.Lazily,
    )

    override fun events(): Flow<E> = events

    public suspend fun interrupt(newStart: E) {
        check(timeOf(newStart) >= time.value) {
            "Can't interrupt generating timeline after observed event"
        }
        startTime = timeOf(newStart)
        startEventFlow.emit(newStart)
    }
}