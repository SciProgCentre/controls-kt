package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.datetime.Instant


public class MergedTimeline<E : TimelineEvent>(
    private val timelines: List<Timeline<E>>
) : Timeline<E> {
    override val time: Instant
        get() = timelines.minOfNotNullOrNull { it.time } ?: Instant.DISTANT_PAST

    override val observedTime: Instant?
        get() = timelines.maxOfNotNullOrNull { it.observedTime }

    override fun flowUnobservedEvents(): Flow<E> = timelines.map { flowUnobservedEvents() }.merge()

    override suspend fun advance(toTime: Instant) {
        timelines.forEach { it.advance(toTime) }
    }

//    override suspend fun interrupt(atTime: Instant) {
//        timelines.forEach { it.interrupt(atTime) }
//    }

    private val observers: MutableSet<TimelineObserver> = mutableSetOf()

    override suspend fun observe(collector: suspend Flow<E>.() -> Unit): TimelineObserver {
        val observer = object : TimelineObserver {
            override var time: Instant = this@MergedTimeline.time

            override suspend fun collect(upTo: Instant) = timelines
                .map { flowUnobservedEvents() }
                .merge()
                .takeWhile { it.time <= upTo }.onEach {
                    time = it.time
                }.collector()


            override fun close() {
                observers.remove(this)
            }

        }
        observers.add(observer)
        return observer
    }
}