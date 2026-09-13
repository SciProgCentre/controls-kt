package space.kscience.simulation

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimelineTests {
    @Test
    fun relativeCollectionContinuesFromTheLastObservedEvent() = runTest(timeout = 3.seconds) {
        val startTime = Instant.parse("2020-01-01T00:00:00.000Z")

        val generation = GeneratingTimeline(
            origin = TimelineEvent(startTime, Unit),
            lookaheadInterval = 1.seconds,
            timeOf = WithTime::time,
            coroutineContext = coroutineContext,
        ) {
            for (step in 1..100) {
                emit(TimelineEvent(startTime + (step * 100).milliseconds, Unit))
            }
        }

        val result = mutableListOf<Duration>()

        val observer = generation.observeEach {
            result.add(it.time - startTime)
        }

        try {
            withTimeout(1.seconds) {
                observer.collect(2.seconds)
                assertEquals((1..20).map { (it * 100).milliseconds }, result)
                observer.collect(2.seconds)
                assertEquals((1..40).map { (it * 100).milliseconds }, result)
                observer.collect(startTime + 8.5.seconds)
                assertEquals((1..85).map { (it * 100).milliseconds }, result)
                assertEquals(startTime + 8.5.seconds, observer.time.value)
            }
        } finally {
            observer.close()
            generation.close()
        }
    }
}
