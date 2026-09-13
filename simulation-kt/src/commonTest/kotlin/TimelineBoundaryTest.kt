package space.kscience.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineBoundaryTest {
    private val startTime = Instant.parse("2020-01-01T00:00:00Z")

    @Test
    fun publicationDoesNotCompareEventPayloads() = boundaryTest {
        class Event(val time: Instant) {
            override fun equals(other: Any?): Boolean = error("Event equality must not be used for publication")
            override fun hashCode(): Int = 0
        }
        val timeline = SharedTimeline(startTime, Event::time, coroutineContext = coroutineContext)
        try {
            timeline.emit(Event(startTime))
            val last = Event(startTime)
            timeline.emit(last)
            assertSame(last, timeline.lastEvent)
        } finally {
            timeline.close()
        }
    }

    private fun event(second: Int, value: Int = second): TimelineEvent<Int> =
        TimelineEvent(startTime + second.seconds, value)

    private fun boundaryTest(block: suspend TestScope.() -> Unit): TestResult = runTest(timeout = 3.seconds) {
        withTimeout(1.seconds) { block() }
    }

    private fun TestScope.shared(): SharedTimeline<TimelineEvent<Int>> =
        SharedTimeline(startTime, TimelineEvent<Int>::time, coroutineContext = coroutineContext)

    private fun TestScope.generating(
        bufferSize: Int = Channel.UNLIMITED,
        generator: suspend TimelineCollector<TimelineEvent<Int>>.(TimelineEvent<Int>) -> Unit,
    ): GeneratingTimeline<TimelineEvent<Int>> = GeneratingTimeline(
        event(0), 200.seconds, TimelineEvent<Int>::time, coroutineContext, bufferSize, generator
    )

    @Test
    fun failureBeforeTheFirstEventDoesNotBecomeSuccessfulCompletion() = boundaryTest {
        val timeline = generating { throw IllegalStateException("source failed before emitting") }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            val failure = assertFailsWith<IllegalStateException> {
                observer.collect(startTime + 1.seconds)
            }
            assertEquals("source failed before emitting", failure.message)
            assertEquals(emptyList(), received)
            assertEquals(startTime, observer.time.value)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun anEqualTimestampCanBePublishedAfterDeliveryBeforeTheRangeCompletes() = boundaryTest {
        val source = shared()
        val timeline: Timeline<TimelineEvent<Int>> = source
        val collector: TimelineCollector<TimelineEvent<Int>> = source
        val firstDelivered = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val observer: TimelineObserver = timeline.observeEach {
            received.add(it.value)
            firstDelivered.complete(Unit)
        }
        try {
            collector.emit(event(1, 10))
            val collection = async { observer.collect(1.seconds) }
            firstDelivered.await()
            assertEquals(event(1).time, timeline.time.value)
            assertFalse(collection.isCompleted)
            collector.emit(event(1, 20))
            source.completeThrough(event(1).time)
            collection.await()
            assertEquals(listOf(10, 20), received)
            assertEquals(event(1, 20), collector.lastEvent)
        } finally {
            observer.close()
            source.close()
        }
    }

    @Test
    fun immediateInterruptsReplaceAnUndeliveredRendezvousWithTheLatestOrigin() = boundaryTest {
        val starts = mutableListOf<Int>()
        val prepared = CompletableDeferred<Unit>()
        val timeline = generating(bufferSize = 0) { origin ->
            starts.add(origin.value)
            if (origin.value == 0) {
                prepared.complete(Unit)
                emit(event(1000))
            } else {
                emit(event(1, origin.value))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime)
            prepared.await()
            assertEquals(emptyList(), received)
            timeline.interrupt(event(0, 1))
            timeline.interrupt(event(0, 2))
            observer.collect(startTime + 1.seconds)
            assertEquals(listOf(0, 2), starts)
            assertEquals(listOf(2), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun cancelingARequestDoesNotCancelItsSuspendedCollectorCallback() = boundaryTest {
        val timeline = shared()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach {
            received.add(it.value)
            if (it.value == 1) {
                entered.complete(Unit)
                release.await()
                exited.complete(Unit)
            }
        }
        try {
            timeline.emit(event(1))
            timeline.emit(event(2))
            timeline.completeThrough(event(2).time)
            val collection = launch { observer.collect(event(2).time) }
            entered.await()
            collection.cancelAndJoin()
            assertFalse(exited.isCompleted)
            release.complete(Unit)
            exited.await()
            observer.collect(event(2).time)
            assertEquals(listOf(1, 2), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun interruptAndCompletionRecheckProofWhileADeliveredCallbackIsSuspended() = boundaryTest {
        for (oldSourceEnds in listOf(false, true)) {
            for (interruptFirst in listOf(false, true)) {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val oldProof = CompletableDeferred<Unit>()
                val timeline = generating { origin ->
                    if (origin.value == 0) {
                        emit(event(1))
                        if (!oldSourceEnds) emit(event(100))
                        oldProof.complete(Unit)
                    } else {
                        emit(event(30))
                    }
                }
                val received = mutableListOf<Int>()
                val observer = timeline.observeEach {
                    received.add(it.value)
                    if (it.value == 1) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                try {
                    val collection = async { observer.collect(event(50).time) }
                    entered.await()
                    oldProof.await()
                    runCurrent()
                    if (interruptFirst) {
                        timeline.interrupt(event(20))
                        release.complete(Unit)
                        collection.await()
                        assertEquals(listOf(1, 30), received)
                    } else {
                        release.complete(Unit)
                        collection.await()
                        assertFailsWith<IllegalStateException> { timeline.interrupt(event(20)) }
                        assertEquals(listOf(1), received)
                    }
                } finally {
                    observer.close()
                    timeline.close()
                }
            }
        }
    }

    @Test
    fun advanceDoesNotIncludeObserversRegisteredAfterItsEntry() = boundaryTest {
        val timeline = shared()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val firstEvents = mutableListOf<Int>()
        val lateEvents = mutableListOf<Int>()
        val first = timeline.observeEach {
            firstEvents.add(it.value)
            if (it.value == 1) {
                entered.complete(Unit)
                release.await()
            }
        }
        try {
            timeline.emit(event(1))
            val advancing = async { timeline.advance(event(2).time) }
            entered.await()
            val late = timeline.observeEach { lateEvents.add(it.value) }
            try {
                timeline.emit(event(2))
                timeline.completeThrough(event(2).time)
                release.complete(Unit)
                advancing.await()
                assertEquals(listOf(1, 2), firstEvents)
                assertEquals(emptyList(), lateEvents)
                late.collect(event(2).time)
                assertEquals(listOf(2), lateEvents)
            } finally {
                late.close()
            }
        } finally {
            first.close()
            timeline.close()
        }
    }

    @Test
    fun cancelingAdvanceLeavesItsObserversAvailableForAnotherAdvance() = boundaryTest {
        val timeline = shared()
        val delivered = List(2) { CompletableDeferred<Unit>() }
        val received = List(2) { mutableListOf<Int>() }
        val observers = List(2) { index ->
            timeline.observeEach {
                received[index].add(it.value)
                delivered[index].complete(Unit)
            }
        }
        try {
            timeline.emit(event(1))
            val advancing = launch { timeline.advance(event(100).time) }
            delivered.forEach { it.await() }
            advancing.cancelAndJoin()
            timeline.emit(event(2))
            timeline.finish()
            timeline.advance(event(2).time)
            assertEquals(listOf(listOf(1, 2), listOf(1, 2)), received)
        } finally {
            observers.forEach { it.close() }
            timeline.close()
        }
    }

    @Test
    fun thePublicTimeThresholdFlowWaitsForAStrictlyLaterThreshold() = boundaryTest {
        val threshold = MutableStateFlow(event(1).time)
        val input: Flow<TimelineEvent<Int>> = flowOf(event(1), event(2))
        val firstDelivered = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val collection = launch {
            input.withTimeThreshold(threshold).collect {
                received.add(it.value)
                firstDelivered.complete(Unit)
            }
        }
        try {
            runCurrent()
            assertEquals(emptyList(), received)
            threshold.value = event(2).time
            firstDelivered.await()
            runCurrent()
            assertEquals(listOf(1), received)
            assertFalse(collection.isCompleted)
            threshold.value = event(3).time
            collection.join()
            assertEquals(listOf(1, 2), received)
        } finally {
            collection.cancelAndJoin()
        }
    }
}
