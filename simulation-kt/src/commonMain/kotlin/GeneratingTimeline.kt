package space.kscience.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * @param lookaheadInterval an interval for generated events ahead of the last observed event.
 */
public class GeneratingTimeline<E : TimelineEvent>(
    private val generationScope: CoroutineScope,
    private val initialEvent: E,
    private val lookaheadInterval: Duration,
    private val generatorChain: suspend (E) -> E
) : Timeline<E> {

    private val mutex = Mutex()

    private val events = ArrayDeque<E>()

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    override val lastEventTime: Instant?
        get() = events.lastOrNull()?.time

    override val observedTime: Instant
        get() = observers.minOfOrNull { it.lastCollectedEventTime } ?: Instant.DISTANT_PAST

    override fun flowUnobservedEvents(): Flow<E> = events.asFlow()

    override suspend fun advance(toTime: Instant) {
        observers.forEach {
            it.collect(toTime)
        }
    }

    private var generatorJob: Job = launchGenerateJob(initialEvent)

    private fun launchGenerateJob(event: E): Job = generationScope.launch {
        var currentEvent = event
        while(currentEvent.time < observedTime + lookaheadInterval) {
            val nextEvent = generatorChain(currentEvent)
            mutex.withLock {
                events.add(nextEvent)
            }
            currentEvent = nextEvent
        }
    }

    private fun regenerate(event: E) {
        generatorJob.cancel()
        generatorJob = launchGenerateJob(event)
    }

    /**
     * Discard unconsumed events after [atTime].
     */
    override suspend fun interrupt(atTime: Instant): Unit {
        if (atTime < observedTime)
            error("Timeline interrupt at time $atTime is not possible because there are observed events before $observedTime")
        mutex.withLock {
            while (events.isNotEmpty() && events.last().time > atTime) {
                events.removeLast()
            }
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
                //cleanup()
            }

            override fun close() {
                observers.remove(this)
            }

        }
        observers.add(observer)
        return observer
    }
}