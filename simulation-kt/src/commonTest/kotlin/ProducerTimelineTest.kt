package space.kscience.simulation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProducerTimelineTest {

    private val start = Instant.parse("2020-01-01T00:00:00Z")
    private fun event(second: Int) = TimelineEvent(start + second.seconds, second)

    private inner class FactoryTimeline(
        context: CoroutineContext,
        private val factory: () -> Flow<TimelineEvent<Int>>,
    ) : ProducerTimeline<TimelineEvent<Int>>(start, WithTime::time, context) {
        var creations = 0

        override fun events(): Flow<TimelineEvent<Int>> {
            creations++
            return factory()
        }
    }

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

    @Test
    fun sourceCreationFailuresReachRequestsAndAllowRestart() = runTest(timeout = 5.seconds) {
        for (failure in listOf(IllegalStateException("Creation failed"), CancellationException("Creation cancelled"))) {
            val uncaught = mutableListOf<Throwable>()
            var failCreation = true
            val timeline = FactoryTimeline(
                backgroundScope.coroutineContext + CoroutineExceptionHandler { _, error -> uncaught.add(error) }
            ) {
                if (failCreation) throw failure
                flowOf(event(3), event(4))
            }
            val received = mutableListOf<TimelineEvent<Int>>()
            val observer = timeline.observeEach { received.add(it) }
            try {
                repeat(2) {
                    val result = runCatching { withTimeout(1.seconds) { observer.collect(event(1).time) } }
                    val actual = result.exceptionOrNull()
                    assertEquals(failure::class, actual?.let { it::class })
                    assertEquals(failure.message, actual?.message)
                }
                assertEquals(1, timeline.creations)
                assertEquals(emptyList(), uncaught)

                failCreation = false
                timeline.state.restart(event(2))
                withTimeout(1.seconds) { observer.collect(event(3).time) }
                assertEquals(listOf(event(3)), received)
                withTimeout(1.seconds) { observer.collect(event(4).time) }
                assertEquals(listOf(event(3), event(4)), received)
                assertEquals(2, timeline.creations)
            } finally {
                observer.close()
                timeline.close()
            }
        }
    }

    @Test
    fun sourceIsCreatedOncePerRequestedGeneration() = runTest(timeout = 5.seconds) {
        var restarted = false
        val timeline = FactoryTimeline(backgroundScope.coroutineContext) {
            if (restarted) flowOf(event(6)) else flowOf(event(1), event(2))
        }
        val received = mutableListOf<TimelineEvent<Int>>()
        val observer = timeline.observeEach { received.add(it) }
        try {
            runCurrent()
            assertEquals(0, timeline.creations)
            observer.collect(event(1).time)
            observer.collect(event(2).time)
            assertEquals(1, timeline.creations)

            restarted = true
            timeline.state.restart(event(5))
            runCurrent()
            assertEquals(1, timeline.creations)
            observer.collect(event(2).time)
            runCurrent()
            assertEquals(1, timeline.creations)

            observer.collect(event(6).time)
            assertEquals(2, timeline.creations)
            assertEquals(listOf(event(1), event(2), event(6)), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun requestsBeforeOriginDoNotCreateSource() = runTest(timeout = 5.seconds) {
        val timeline = FactoryTimeline(backgroundScope.coroutineContext) { flowOf(event(11)) }
        timeline.state.restart(event(10))
        val observer = timeline.observeEach { }
        try {
            observer.collect(event(5).time)
            runCurrent()
            assertEquals(0, timeline.creations)
            observer.collect(event(10).time)
            assertEquals(1, timeline.creations)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun directSourceRemainsOpenAfterCollectionStarts() = runTest(timeout = 5.seconds) {
        val timeline = SharedTimeline<TimelineEvent<Int>>(
            start, WithTime::time, coroutineContext = backgroundScope.coroutineContext
        )
        val received = mutableListOf<TimelineEvent<Int>>()
        val observer = timeline.observeEach { received.add(it) }
        try {
            val request = async { observer.collect(event(1).time) }
            runCurrent()
            assertFalse(request.isCompleted)
            timeline.emit(event(1))
            timeline.completeThrough(event(1).time)
            request.await()

            timeline.emit(event(2))
            timeline.finish()
            observer.collect(event(2).time)
            assertEquals(listOf(event(1), event(2)), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }
}
