package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

/**
 * A manually mutable [Timeline] that could be modified via [emit] method by multiple
 */
public class SharedTimeline<E : TimelineEvent> : Timeline<E> {

    private val mutex = Mutex()

    private val events = ArrayDeque<E>()

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    override val lastEventTime: Instant?
        get() = events.lastOrNull()?.time

    override val observedTime: Instant
        get() = observers.minOfOrNull { it.lastCollectedEventTime } ?: Instant.DISTANT_PAST

    override fun flowUnobservedEvents(): Flow<E> = events.asFlow()

    /**
     * Emit new event to the timeline
     */
    public suspend fun emit(event: E): Boolean = mutex.withLock {
        if (event.time < observedTime) error("Can't emit event $event because there are observed events after $observedTime")
        events.add(event)
    }

    override suspend fun advance(toTime: Instant) {
        observers.forEach {
            it.collect(toTime)
        }
    }

    /**
     * Discard all events before [observedTime]
     */
    private suspend fun cleanup(): Unit {
        val threshold = observedTime
        while (events.isNotEmpty() && events.last().time > threshold) {
            events.removeFirst()
        }
    }

    /**
     * Discard unconsumed events after [atTime].
     */
    override suspend fun interrupt(atTime: Instant): Unit = mutex.withLock {
        val threshold = observedTime
        if (atTime < threshold)
            error("Timeline interrupt at time $atTime is not possible because there are observed events before $threshold")
        while (events.isNotEmpty() && events.last().time > atTime) {
            events.removeLast()
        }
    }

    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val observer = object : TimelineObserver {
            val observerMutex = Mutex()
            override var lastCollectedEventTime: Instant = Instant.DISTANT_PAST

            override suspend fun collect(upTo: Instant) = observerMutex.withLock {
                flowUnobservedEvents().takeWhile { it.time <= upTo }.onEach {
                    lastCollectedEventTime = it.time
                }.collector()
                cleanup()
            }

            override fun close() {
                observers.remove(this)
            }

        }
        observers.add(observer)
        return observer
    }
}