package space.kscience.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GeneratingTimelineTest {

    private val startTime = Instant.parse("2020-01-01T00:00:00.000Z")

    @Test
    fun testInterruptStopsStaleGenerationAtNextEmission() = runTest(timeout = 30.seconds) {
        val oldGenerationStarted = CompletableDeferred<Unit>()
        val resumeOldGeneration = CompletableDeferred<Unit>()
        val newEventObserved = CompletableDeferred<Unit>()
        val generatedOrigins = mutableListOf<Int>()
        var staleGenerationContinued = false
        val timeline = GeneratingTimeline(
            origin = TimelineEvent(startTime, 0),
            lookaheadInterval = 10.seconds,
            timeOf = WithTime::time,
            coroutineContext = coroutineContext,
        ) { origin ->
            generatedOrigins.add(origin.value)
            if (origin.value == 0) {
                oldGenerationStarted.complete(Unit)
                resumeOldGeneration.await()
                emit(TimelineEvent(startTime + 1.seconds, 0))
                staleGenerationContinued = true
            } else {
                emit(TimelineEvent(startTime + 2.seconds, 1))
                emit(TimelineEvent(startTime + 3.seconds, 1))
            }
        }
        val branches = mutableListOf<Int>()
        val observer = timeline.observeEach {
            branches.add(it.value)
            if (it.value == 1) newEventObserved.complete(Unit)
        }
        val collection = launch { observer.collect(startTime + 2.seconds) }
        try {
            oldGenerationStarted.await()
            timeline.interrupt(TimelineEvent(startTime + 1.seconds, 1))
            resumeOldGeneration.complete(Unit)

            collection.join()
            newEventObserved.await()

            assertFalse(staleGenerationContinued, "the stale generation continued after emitting")
            assertEquals(listOf(0, 1), generatedOrigins)
            assertEquals(listOf(1), branches)
        } finally {
            collection.cancelAndJoin()
            observer.close()
            timeline.close()
        }
    }
}
