package space.kscience.simulation

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GeneratingTimelineTest {

    private val startTime = Instant.parse("2020-01-01T00:00:00.000Z")

    /**
     * Generate a tick each 0.1 seconds of model time. The value of an event marks the branch it is generated from.
     */
    private fun ticks(): GeneratingTimeline<TimelineEvent<Int>> = GeneratingTimeline(
        origin = TimelineEvent(startTime, 0),
        lookaheadInterval = 1.seconds,
        timeOf = WithTime::time,
    ) { origin ->
        var time = origin.time
        while (currentCoroutineContext().isActive) {
            time += 0.1.seconds
            emit(TimelineEvent(time, origin.value))
        }
    }

    @Test
    fun testInterruptRestartsGenerationWithinLookahead() = runTest(timeout = 30.seconds) {
        val timeline = ticks()
        val branches = mutableListOf<Int>()
        val observer = timeline.observeEach { branches.add(it.value) }
        try {
            observer.collect(startTime + 1.seconds)
            assertEquals(setOf(0), branches.toSet())

            timeline.interrupt(TimelineEvent(startTime + 5.seconds, 1))
            observer.collect(startTime + 6.seconds)

            assertTrue(1 in branches, "the new branch produced no events")
        } finally {
            observer.close()
            timeline.close()
        }
    }
}
