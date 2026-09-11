package space.kscience.simulation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProducerTimelineTest {

    private class SilentTimeline(startTime: Instant) : ProducerTimeline<TimelineEvent<Unit>>(
        startTime = startTime,
        timeOf = WithTime::time,
        coroutineContext = EmptyCoroutineContext
    ) {
        override fun events(): Flow<TimelineEvent<Unit>> = emptyFlow()
    }

    @Test
    fun testCloseWithSeveralObservers() = runTest(timeout = 5.seconds) {
        val timeline = SilentTimeline(Instant.parse("2020-01-01T00:00:00.000Z"))
        val observed = mutableListOf<TimelineEvent<Unit>>()
        timeline.observeEach { observed.add(it) }
        timeline.observeEach { observed.add(it) }

        timeline.close()

        assertEquals(emptyList(), observed)
    }
}
