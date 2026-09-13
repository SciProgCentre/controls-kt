package space.kscience.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineContractTest {
    private val startTime = Instant.parse("2020-01-01T00:00:00Z")

    private fun event(second: Int, value: Int = second): TimelineEvent<Int> =
        TimelineEvent(startTime + second.seconds, value)

    private fun assertFailure(expected: Throwable, actual: Throwable?) {
        assertEquals(expected::class, actual?.let { it::class })
        assertEquals(expected.message, actual?.message)
    }

    private fun contractTest(block: suspend TestScope.() -> Unit): TestResult = runTest(timeout = 3.seconds) {
        withTimeout(1.seconds) { block() }
    }

    private fun TestScope.shared(bufferSize: Int = Channel.UNLIMITED): SharedTimeline<TimelineEvent<Int>> =
        SharedTimeline(startTime, TimelineEvent<Int>::time, bufferSize, coroutineContext)

    private fun TestScope.generating(
        origin: TimelineEvent<Int> = event(0),
        lookahead: Duration = 1.seconds,
        bufferSize: Int = Channel.UNLIMITED,
        generator: suspend TimelineCollector<TimelineEvent<Int>>.(TimelineEvent<Int>) -> Unit,
    ): GeneratingTimeline<TimelineEvent<Int>> = GeneratingTimeline(
        origin, lookahead, TimelineEvent<Int>::time, coroutineContext, bufferSize, generator
    )

    @Test
    fun consecutiveCollectionsPreserveEveryEvent() = runTest(timeout = 3.seconds) {
        val timeline = object : ProducerTimeline<TimelineEvent<Int>>(
            startTime, TimelineEvent<Int>::time, coroutineContext
        ) {
            override fun events(): Flow<TimelineEvent<Int>> = flow {
                for (second in 1..7) emit(event(second))
            }
        }
        val received = mutableListOf<Int>()
        val delivered = (1..6).associateWith { CompletableDeferred<Unit>() }
        val observer = timeline.observeEach {
            received.add(it.value)
            delivered.getValue(it.value).complete(Unit)
        }

        try {
            withTimeout(1.seconds) {
                for (boundary in listOf(2, 4, 6)) {
                    observer.collect(startTime + boundary.seconds)
                    delivered.getValue(boundary).await()
                    assertEquals((1..boundary).toList(), received)
                }
                observer.collect(startTime + 6.seconds)
                assertEquals((1..6).toList(), received)
            }
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun sparseGenerationCompletesBeforeTheNextEvent() = runTest(timeout = 3.seconds) {
        val started = CompletableDeferred<Unit>()
        val timeline = GeneratingTimeline(
            origin = event(0),
            lookaheadInterval = 1.seconds,
            timeOf = TimelineEvent<Int>::time,
            coroutineContext = coroutineContext,
        ) {
            started.complete(Unit)
            for (second in 5..20 step 5) emit(event(second))
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }

        try {
            withTimeout(1.seconds) {
                val collection = async { observer.collect(startTime + 3.seconds) }
                started.await()
                collection.await()
            }
            assertEquals(emptyList(), received)
            assertEquals(startTime, observer.time.value)
            assertEquals(startTime, timeline.time.value)
            withTimeout(1.seconds) { observer.collect(startTime + 20.seconds) }
            assertEquals(listOf(5, 10, 15, 20), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun finiteAndEmptyGenerationCompletePastTheirEnd() = runTest(timeout = 3.seconds) {
        for (seconds in listOf(emptyList(), listOf(1, 2))) {
            val completed = CompletableDeferred<Unit>()
            val timeline = GeneratingTimeline(
                origin = event(0),
                lookaheadInterval = 10.seconds,
                timeOf = TimelineEvent<Int>::time,
                coroutineContext = coroutineContext,
            ) {
                for (second in seconds) emit(event(second))
                completed.complete(Unit)
            }
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }

            try {
                withTimeout(1.seconds) {
                    val collection = async { observer.collect(startTime + 20.seconds) }
                    completed.await()
                    collection.await()
                }
                assertEquals(seconds, received)
                assertEquals(event(seconds.lastOrNull() ?: 0).time, observer.time.value)
            } finally {
                observer.close()
                timeline.close()
            }
        }
    }

    @Test
    fun lateReadersStartAfterTheProtectedPrefixAndLaggingReadersRetainIt() = contractTest {
        val timeline = shared()
        val fastEvents = mutableListOf<Int>()
        val slowEvents = mutableListOf<Int>()
        val lateEvents = mutableListOf<Int>()
        val fast = timeline.observeEach { fastEvents.add(it.value) }
        val slow = timeline.observeEach { slowEvents.add(it.value) }
        try {
            for (second in 1..6) timeline.emit(event(second))
            timeline.completeThrough(startTime + 6.seconds)
            fast.collect(startTime + 2.seconds)
            val late = timeline.observeEach { lateEvents.add(it.value) }
            try {
                assertEquals(startTime + 2.seconds, late.time.value)
                fast.collect(startTime + 6.seconds)
                fast.close()
                slow.collect(startTime + 6.seconds)
                late.collect(startTime + 6.seconds)
                assertEquals((1..6).toList(), fastEvents)
                assertEquals((1..6).toList(), slowEvents)
                assertEquals((3..6).toList(), lateEvents)
                assertEquals(startTime + 6.seconds, timeline.time.value)
            } finally {
                late.close()
            }
        } finally {
            fast.close()
            slow.close()
            timeline.close()
        }
    }

    @Test
    fun sparseFutureEventRemainsAvailableAfterAnEmptyTail() = contractTest {
        val timeline = generating {
            emit(event(4))
            emit(event(100))
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime + 10.seconds)
            assertEquals(listOf(4), received)
            observer.collect(startTime + 50.seconds)
            assertEquals(listOf(4), received)
            observer.collect(startTime + 100.seconds)
            assertEquals(listOf(4, 100), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun equalTimestampsAreDeliveredBeforeTheRangeCompletes() = contractTest {
        for (lookahead in listOf(Duration.ZERO, 1.seconds)) {
            val timeline = generating(lookahead = lookahead, bufferSize = 2) {
                for (id in 1..20) emit(event(1, id))
            }
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }
            try {
                observer.collect(startTime + 1.seconds)
                assertEquals((1..20).toList(), received)
                observer.collect(startTime + 1.seconds)
                assertEquals((1..20).toList(), received)
                timeline.interrupt(event(1))
                assertFailsWith<IllegalStateException> { observer.collect(startTime + 2.seconds) }
            } finally {
                observer.close()
                timeline.close()
            }
        }
    }

    @Test
    fun closingTheLaggingReaderReleasesFiniteCapacity() = contractTest {
        for (capacity in listOf(1, 2, 3, 16)) {
            val timeline = shared(capacity)
            val received = mutableListOf<Int>()
            val fast = timeline.observeEach { received.add(it.value) }
            val slow = timeline.observeEach { }
            try {
                for (second in 1..capacity) timeline.emit(event(second))
                timeline.completeThrough(startTime + capacity.seconds)
                fast.collect(startTime + capacity.seconds)
                val attempted = CompletableDeferred<Unit>()
                val pending = async {
                    attempted.complete(Unit)
                    timeline.emit(event(capacity + 1))
                }
                attempted.await()
                runCurrent()
                assertFalse(pending.isCompleted)
                slow.close()
                pending.await()
                timeline.finish()
                fast.collect(startTime + (capacity + 1).seconds)
                assertEquals((1..capacity + 1).toList(), received)
                assertEquals(event(capacity + 1), timeline.lastEvent)
            } finally {
                fast.close()
                slow.close()
                timeline.close()
            }
        }
    }

    @Test
    fun finiteCapacityRetainsUnobservedEventsUntilAReaderArrives() = contractTest {
        for (capacity in listOf(1, 2, 3, 16)) {
            val timeline = shared(capacity)
            try {
                for (second in 1..capacity) timeline.emit(event(second))
                val attempted = CompletableDeferred<Unit>()
                val pending = async {
                    attempted.complete(Unit)
                    timeline.emit(event(capacity + 1))
                }
                attempted.await()
                runCurrent()
                assertFalse(pending.isCompleted)
                val received = mutableListOf<Int>()
                val observer = timeline.observeEach { received.add(it.value) }
                try {
                    val collection = async { observer.collect(startTime + (capacity + 1).seconds) }
                    pending.await()
                    timeline.finish()
                    collection.await()
                    assertEquals((1..capacity + 1).toList(), received)
                } finally {
                    observer.close()
                }
            } finally {
                timeline.close()
            }
        }
    }

    @Test
    fun rendezvousIncludesReadersRegisteredBeforeItsFirstDelivery() = contractTest {
        val timeline = shared(0)
        val firstEvents = mutableListOf<Int>()
        val secondEvents = mutableListOf<Int>()
        try {
            val pending = async { timeline.emit(event(1)) }
            runCurrent()
            assertFalse(pending.isCompleted)
            assertEquals(null, timeline.lastEvent)
            val first = timeline.observeEach { firstEvents.add(it.value) }
            val second = timeline.observeEach { secondEvents.add(it.value) }
            try {
                val firstCollection = async { first.collect(startTime + 1.seconds) }
                runCurrent()
                assertEquals(listOf(1), firstEvents)
                assertFalse(pending.isCompleted)
                val lateEvents = mutableListOf<Int>()
                val late = timeline.observeEach { lateEvents.add(it.value) }
                try {
                    val secondCollection = async { second.collect(startTime + 1.seconds) }
                    pending.await()
                    timeline.finish()
                    firstCollection.await()
                    secondCollection.await()
                    late.collect(startTime + 1.seconds)
                    assertEquals(listOf(1), secondEvents)
                    assertEquals(emptyList(), lateEvents)
                    assertEquals(event(1), timeline.lastEvent)
                } finally {
                    late.close()
                }
            } finally {
                first.close()
                second.close()
            }
        } finally {
            timeline.close()
        }
    }

    @Test
    fun cancellationAfterFirstRendezvousDeliveryPreservesRemainingRecipients() = contractTest {
        val timeline = shared(0)
        val firstDelivered = CompletableDeferred<Unit>()
        val secondEvents = mutableListOf<Int>()
        val first = timeline.observeEach { firstDelivered.complete(Unit) }
        val second = timeline.observeEach { secondEvents.add(it.value) }
        try {
            val pending = launch { timeline.emit(event(1)) }
            val firstCollection = async { first.collect(startTime + 1.seconds) }
            firstDelivered.await()
            pending.cancelAndJoin()
            timeline.finish()
            second.collect(startTime + 1.seconds)
            firstCollection.await()
            assertEquals(listOf(1), secondEvents)
            assertEquals(event(1), timeline.lastEvent)
        } finally {
            first.close()
            second.close()
            timeline.close()
        }
    }

    @Test
    fun canceledUnacceptedRendezvousDoesNotPublishAnEvent() = contractTest {
        val timeline = shared(0)
        try {
            val pending = launch { timeline.emit(event(1)) }
            runCurrent()
            pending.cancelAndJoin()
            timeline.finish()
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }
            try {
                observer.collect(startTime + 2.seconds)
                assertEquals(emptyList(), received)
                assertEquals(null, timeline.lastEvent)
            } finally {
                observer.close()
            }
        } finally {
            timeline.close()
        }
    }

    @Test
    fun releasedHistoryDoesNotForgetTheLastAcceptedTimestamp() = contractTest {
        val timeline = shared(1)
        val observer = timeline.observeEach { }
        try {
            timeline.emit(event(10))
            timeline.completeThrough(startTime + 10.seconds)
            observer.collect(startTime + 10.seconds)
            assertEquals(event(10), timeline.lastEvent)
            assertFailsWith<IllegalStateException> { timeline.emit(event(9)) }
            timeline.emit(event(11))
            timeline.finish()
            observer.collect(startTime + 11.seconds)
            assertEquals(event(11), timeline.lastEvent)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun generationStopsAcceptingAtTheConfiguredCapacity() = contractTest {
        for (capacity in listOf(1, 2, 3, 16)) {
            val attempted = Channel<Int>(Channel.UNLIMITED)
            var accepted = 0
            val timeline = generating(bufferSize = capacity) {
                for (second in 1..32) {
                    attempted.send(second)
                    emit(event(second))
                    accepted++
                }
            }
            val received = mutableListOf<Int>()
            val fast = timeline.observeEach { received.add(it.value) }
            val slow = timeline.observeEach { }
            try {
                val collection = async { fast.collect(startTime + 32.seconds) }
                repeat(capacity + 1) { assertEquals(it + 1, attempted.receive()) }
                runCurrent()
                assertEquals(capacity, accepted)
                assertFalse(collection.isCompleted)
                slow.close()
                collection.await()
                assertEquals((1..32).toList(), received)
            } finally {
                fast.close()
                slow.close()
                timeline.close()
            }
        }
    }

    @Test
    fun zeroLookaheadPreparesOnlyTheNextEventWithoutFurtherDemand() = contractTest {
        for (capacity in listOf(0, 1, 2)) {
            val prepared = mutableListOf<Int>()
            val timeline = generating(lookahead = Duration.ZERO, bufferSize = capacity) {
                for (second in 1..100) {
                    prepared.add(second)
                    emit(event(second))
                }
            }
            val observer = timeline.observeEach { }
            try {
                runCurrent()
                assertEquals(emptyList(), prepared)
                observer.collect(startTime)
                runCurrent()
                assertEquals(listOf(1), prepared)
            } finally {
                observer.close()
                timeline.close()
            }
        }
    }

    @Test
    fun manualProgressRejectsAnOvertakenPublicationWithoutFailingTheSource() = contractTest {
        for (capacity in listOf(0, 1, 2)) {
            val timeline = shared(capacity)
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }
            try {
                for (second in 1..capacity) timeline.emit(event(second))
                val pending = async { runCatching { timeline.emit(event(capacity + 1)) } }
                runCurrent()
                assertFalse(pending.isCompleted)
                timeline.completeThrough(startTime + (capacity + 1).seconds)
                timeline.completeThrough(startTime + capacity.seconds)
                timeline.completeThrough(startTime + (capacity + 1).seconds)
                assertIs<IllegalStateException>(pending.await().exceptionOrNull())
                observer.collect(startTime + (capacity + 1).seconds)
                assertEquals((1..capacity).toList(), received)
                val next = async { timeline.emit(event(capacity + 2)) }
                val collection = async { observer.collect(startTime + (capacity + 2).seconds) }
                next.await()
                timeline.finish()
                collection.await()
                assertEquals((1..capacity).toList() + (capacity + 2), received)
            } finally {
                observer.close()
                timeline.close()
            }
        }
    }

    @Test
    fun finishBeforeRegistrationPreservesAcceptedEventsAndRejectsPendingOnes() = contractTest {
        for (capacity in listOf(0, 1, 2)) {
            val timeline = shared(capacity)
            try {
                for (second in 1..capacity) timeline.emit(event(second))
                val pending = async { runCatching { timeline.emit(event(capacity + 1)) } }
                runCurrent()
                timeline.finish()
                timeline.finish()
                assertIs<IllegalStateException>(pending.await().exceptionOrNull())
                assertFailsWith<IllegalStateException> { timeline.emit(event(capacity + 2)) }
                val received = mutableListOf<Int>()
                val observer = timeline.observeEach { received.add(it.value) }
                try {
                    observer.collect(startTime + 100.seconds)
                    assertEquals((1..capacity).toList(), received)
                    assertEquals(event(capacity).takeIf { capacity > 0 }, timeline.lastEvent)
                } finally {
                    observer.close()
                }
            } finally {
                timeline.close()
            }
        }
    }

    @Test
    fun progressBeforeRegistrationCompletesAnEmptyRangeWithoutMovingTime() = contractTest {
        val timeline = shared()
        try {
            timeline.completeThrough(startTime + 50.seconds)
            val observer = timeline.observeEach { }
            try {
                observer.collect(startTime + 50.seconds)
                assertEquals(startTime, observer.time.value)
                assertEquals(startTime, timeline.time.value)
                assertFailsWith<IllegalStateException> { timeline.emit(event(50)) }
                timeline.emit(event(60))
                timeline.finish()
                observer.collect(startTime + 60.seconds)
                assertEquals(startTime + 60.seconds, observer.time.value)
            } finally {
                observer.close()
            }
        } finally {
            timeline.close()
        }
    }

    @Test
    fun closeCancelsAllWaitingReadersWithoutCancelingTheirCaller() = contractTest {
        for (capacity in listOf(0, 1, 2)) {
            val timeline = shared(capacity)
            val observers = List(3) { timeline.observeEach { } }
            try {
                val pending = observers.map { observer ->
                    async { runCatching { observer.collect(startTime + 10.seconds) } }
                }
                runCurrent()
                timeline.close()
                timeline.close()
                for (collection in pending) {
                    assertIs<CancellationException>(collection.await().exceptionOrNull())
                }
                assertTrue(coroutineContext[Job]!!.isActive)
                assertFailsWith<IllegalStateException> { timeline.emit(event(1)) }
                assertFailsWith<IllegalStateException> { timeline.observeEach { } }
            } finally {
                observers.forEach { it.close() }
                timeline.close()
            }
        }
    }

    @Test
    fun closeCancelsPublicationsWaitingForCapacity() = contractTest {
        for (capacity in listOf(0, 1, 2)) {
            val timeline = shared(capacity)
            try {
                for (second in 1..capacity) timeline.emit(event(second))
                val pending = async { runCatching { timeline.emit(event(capacity + 1)) } }
                runCurrent()
                assertFalse(pending.isCompleted)
                timeline.close()
                assertIs<CancellationException>(pending.await().exceptionOrNull())
                assertTrue(coroutineContext[Job]!!.isActive)
            } finally {
                timeline.close()
            }
        }
    }

    @Test
    fun cancelingTheObservingCallerClosesOnlyItsRegistration() = contractTest {
        val timeline = shared(1)
        val registered = CompletableDeferred<Unit>()
        val caller = launch {
            timeline.observeEach { }
            registered.complete(Unit)
            awaitCancellation()
        }
        try {
            registered.await()
            timeline.emit(event(1))
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }
            try {
                timeline.completeThrough(startTime + 1.seconds)
                observer.collect(startTime + 1.seconds)
                val pending = async { timeline.emit(event(2)) }
                runCurrent()
                assertFalse(pending.isCompleted)
                caller.cancelAndJoin()
                pending.await()
                timeline.finish()
                observer.collect(startTime + 2.seconds)
                assertEquals(listOf(1, 2), received)
                assertTrue(coroutineContext[Job]!!.isActive)
            } finally {
                observer.close()
            }
        } finally {
            caller.cancelAndJoin()
            timeline.close()
        }
    }

    @Test
    fun earlyCollectorCompletionAndFailureReleaseTheirRegistrations() = contractTest {
        for (failure in listOf<RuntimeException?>(null, IllegalStateException("collector failed"))) {
            val timeline = shared(1)
            val received = mutableListOf<Int>()
            val observer = timeline.observe {
                take(1).collect {
                    received.add(it.value)
                    if (failure != null) throw failure
                }
            }
            try {
                timeline.emit(event(1))
                if (failure == null) {
                    assertFailsWith<CancellationException> { observer.collect(startTime + 2.seconds) }
                } else {
                    val actual = assertFailsWith<IllegalStateException> { observer.collect(startTime + 2.seconds) }
                    assertFailure(failure, actual)
                }
                assertEquals(listOf(1), received)
                timeline.emit(event(2))
            } finally {
                observer.close()
                timeline.close()
            }
        }
    }

    @Test
    fun cancelingARequestPreservesDeliveredEventsAndAllowsTheNextRequest() = contractTest {
        val timeline = shared()
        val delivered = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach {
            received.add(it.value)
            delivered.complete(Unit)
        }
        try {
            timeline.emit(event(1))
            val pending = launch { observer.collect(startTime + 100.seconds) }
            delivered.await()
            pending.cancelAndJoin()
            timeline.emit(event(2))
            timeline.finish()
            observer.collect(startTime + 2.seconds)
            assertEquals(listOf(1, 2), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun completedEmptyRangesRemainProtectedAcrossRestarts() = contractTest {
        var starts = 0
        val timeline = generating { origin ->
            starts++
            if (origin.value != 0) emit(event(60))
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime + 50.seconds)
            assertEquals(startTime, observer.time.value)
            assertEquals(startTime, timeline.time.value)
            assertFailsWith<IllegalStateException> { timeline.interrupt(event(20)) }
            timeline.interrupt(event(50, 1))
            observer.collect(startTime + 40.seconds)
            observer.collect(startTime + 50.seconds)
            observer.collect(50.seconds)
            runCurrent()
            assertEquals(1, starts)
            assertEquals(emptyList(), received)
            observer.collect(startTime + 60.seconds)
            assertEquals(listOf(60), received)
            assertEquals(2, starts)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun unfinishedRequestsDoNotProtectTheirRequestedHorizon() = contractTest {
        val oldStarted = CompletableDeferred<Unit>()
        val timeline = generating { origin ->
            if (origin.value == 0) {
                oldStarted.complete(Unit)
                awaitCancellation()
            } else {
                emit(event(30))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            val collection = async { observer.collect(startTime + 100.seconds) }
            oldStarted.await()
            timeline.interrupt(event(20))
            collection.await()
            assertEquals(listOf(30), received)
            assertEquals(startTime + 30.seconds, observer.time.value)
            assertFailsWith<IllegalStateException> { timeline.interrupt(event(50)) }
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun originProvesOnlyTheRangeStrictlyBeforeItsTimestamp() = contractTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val timeline = generating {
            started.complete(Unit)
            release.await()
            emit(event(50))
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            timeline.interrupt(event(50))
            observer.collect(startTime + 49.seconds)
            assertFalse(started.isCompleted)
            val collection = async { observer.collect(startTime + 50.seconds) }
            started.await()
            runCurrent()
            assertFalse(collection.isCompleted)
            release.complete(Unit)
            collection.await()
            assertEquals(listOf(50), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun interruptRestartsGenerationBlockedBeyondLookahead() = contractTest {
        val prepared = CompletableDeferred<Unit>()
        val timeline = generating { origin ->
            if (origin.value == 0) {
                emit(event(1))
                prepared.complete(Unit)
                emit(event(1000))
            } else {
                emit(event(2))
                emit(event(3))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime + 1.seconds)
            prepared.await()
            runCurrent()
            timeline.interrupt(event(2))
            observer.collect(startTime + 3.seconds)
            assertEquals(listOf(1, 2, 3), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun returningToAnEqualOriginDoesNotReviveAnEarlierGeneration() = contractTest {
        val middleStarted = CompletableDeferred<Unit>()
        var initialStarts = 0
        val timeline = generating { origin ->
            if (origin.value == 1) {
                middleStarted.complete(Unit)
                awaitCancellation()
            } else if (initialStarts++ == 0) {
                emit(event(1000, 10))
            } else {
                emit(event(1, 30))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime)
            timeline.interrupt(event(0, 1))
            val collection = async { observer.collect(startTime + 1.seconds) }
            middleStarted.await()
            timeline.interrupt(event(0))
            collection.await()
            assertEquals(listOf(30), received)
            assertEquals(2, initialStarts)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun interruptPreservesThePrefixStillNeededByALaggingReader() = contractTest {
        val prepared = CompletableDeferred<Unit>()
        val timeline = generating(lookahead = 10.seconds) { origin ->
            if (origin.value == 0) {
                for (second in 1..6) emit(event(second))
                prepared.complete(Unit)
            } else {
                emit(event(3, 30))
                emit(event(4, 40))
            }
        }
        val fastEvents = mutableListOf<Int>()
        val slowEvents = mutableListOf<Int>()
        val fast = timeline.observeEach { fastEvents.add(it.value) }
        val slow = timeline.observeEach { slowEvents.add(it.value) }
        try {
            fast.collect(startTime + 2.seconds)
            prepared.await()
            timeline.interrupt(event(2))
            fast.collect(startTime + 4.seconds)
            fast.close()
            slow.collect(startTime + 4.seconds)
            assertEquals(listOf(1, 2, 30, 40), fastEvents)
            assertEquals(listOf(1, 2, 30, 40), slowEvents)
            assertFailsWith<IllegalStateException> { timeline.interrupt(event(3)) }
        } finally {
            fast.close()
            slow.close()
            timeline.close()
        }
    }

    @Test
    fun aProducerCanInterruptItselfWithoutWaitingForItsOwnCompletion() = contractTest {
        lateinit var timeline: GeneratingTimeline<TimelineEvent<Int>>
        timeline = generating { origin ->
            if (origin.value == 0) timeline.interrupt(event(1)) else emit(event(2))
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime + 2.seconds)
            assertEquals(listOf(2), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun cancelingTheInterruptCallerDoesNotLoseTheCommittedRestart() = contractTest {
        val oldStarted = CompletableDeferred<Unit>()
        val committed = CompletableDeferred<Unit>()
        val timeline = generating { origin ->
            if (origin.value == 0) {
                oldStarted.complete(Unit)
                awaitCancellation()
            } else {
                emit(event(2))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            val collection = async { observer.collect(startTime + 2.seconds) }
            oldStarted.await()
            val caller = launch {
                timeline.interrupt(event(1))
                committed.complete(Unit)
                awaitCancellation()
            }
            committed.await()
            caller.cancelAndJoin()
            collection.await()
            assertEquals(listOf(2), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun sourceFailureDoesNotEraseProofOfEarlierRangesOrBecomeEndOfStream() = contractTest {
        val failure = IllegalStateException("generation failed")
        val timeline = generating(lookahead = 200.seconds) { origin ->
            if (origin.value == 0) {
                emit(event(4))
                emit(event(100))
                throw failure
            } else {
                emit(event(101))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach { received.add(it.value) }
        try {
            observer.collect(startTime + 10.seconds)
            observer.collect(startTime + 50.seconds)
            assertEquals(listOf(4), received)
            val actual = assertFailsWith<IllegalStateException> { observer.collect(startTime + 101.seconds) }
            assertFailure(failure, actual)
            assertEquals(listOf(4, 100), received)
            timeline.interrupt(event(100))
            observer.collect(startTime + 101.seconds)
            assertEquals(listOf(4, 100, 101), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun cancelingTheOwnerContextWakesAPublicationWithoutAnyObservers() = contractTest {
        val ownerJob = Job(coroutineContext[Job])
        val timeline = SharedTimeline(
            startTime, TimelineEvent<Int>::time, 1, coroutineContext + ownerJob
        )
        try {
            timeline.emit(event(1))
            val pending = async { runCatching { timeline.emit(event(2)) } }
            runCurrent()
            assertFalse(pending.isCompleted)
            ownerJob.cancelAndJoin()
            assertIs<CancellationException>(pending.await().exceptionOrNull())
            assertFailsWith<IllegalStateException> { timeline.emit(event(3)) }
            assertTrue(coroutineContext[Job]!!.isActive)
        } finally {
            timeline.close()
            ownerJob.cancelAndJoin()
        }
    }

    @Test
    fun canceledPartialRequestsProtectOnlyTheirDeliveredPrefix() = contractTest {
        val delivered = CompletableDeferred<Unit>()
        val timeline = generating { origin ->
            if (origin.value == 0) {
                emit(event(1))
                awaitCancellation()
            } else {
                emit(event(3))
            }
        }
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach {
            received.add(it.value)
            delivered.complete(Unit)
        }
        try {
            val collection = launch { observer.collect(startTime + 100.seconds) }
            delivered.await()
            collection.cancelAndJoin()
            timeline.interrupt(event(2))
            observer.collect(startTime + 3.seconds)
            assertEquals(listOf(1, 3), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun cancelingAfterCompletionDoesNotReopenTheCompletedRange() = contractTest {
        val timeline = generating { }
        val observer = timeline.observeEach { }
        val completed = CompletableDeferred<Unit>()
        try {
            val caller = launch {
                observer.collect(startTime + 50.seconds)
                completed.complete(Unit)
                awaitCancellation()
            }
            completed.await()
            caller.cancelAndJoin()
            assertFailsWith<IllegalStateException> { timeline.interrupt(event(20)) }
            assertEquals(startTime, timeline.time.value)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun collectorCallbacksCanPublishProgressWithoutHoldingTheOwnerLock() = contractTest {
        val timeline = shared(1)
        val received = mutableListOf<Int>()
        val observer = timeline.observeEach {
            received.add(it.value)
            timeline.completeThrough(it.time)
        }
        try {
            timeline.emit(event(1))
            observer.collect(startTime + 1.seconds)
            assertEquals(listOf(1), received)
        } finally {
            observer.close()
            timeline.close()
        }
    }

    @Test
    fun aCustomCallbackFlowProducerReleasesItsSubscriptionOnClose() = contractTest {
        val closed = CompletableDeferred<Unit>()
        var subscriptions = 0
        val timeline = object : ProducerTimeline<TimelineEvent<Int>>(
            startTime, TimelineEvent<Int>::time, coroutineContext
        ) {
            override fun events(): Flow<TimelineEvent<Int>> = callbackFlow {
                subscriptions++
                send(event(1))
                awaitClose { closed.complete(Unit) }
            }
        }
        val observer = timeline.observeEach { }
        try {
            observer.collect(startTime)
            observer.collect(startTime)
            assertEquals(1, subscriptions)
        } finally {
            observer.close()
            timeline.close()
        }
        closed.await()
    }

    @Test
    fun advancingWithoutObserversDoesNotStartGenerationOrProtectARange() = contractTest {
        var starts = 0
        val timeline = generating {
            starts++
            emit(event(1))
        }
        try {
            timeline.advance(startTime + 100.seconds)
            runCurrent()
            assertEquals(0, starts)
            timeline.interrupt(event(0))
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }
            try {
                observer.collect(startTime + 1.seconds)
                assertEquals(listOf(1), received)
            } finally {
                observer.close()
            }
        } finally {
            timeline.close()
        }
    }

    @Test
    fun aFailedAdvanceCancelsSiblingRequestsWithoutClosingHealthyObservers() = contractTest {
        val failure = IllegalStateException("collector failed")
        val healthyDelivered = CompletableDeferred<Unit>()
        val timeline = shared()
        val received = mutableListOf<Int>()
        val healthy = timeline.observeEach {
            received.add(it.value)
            healthyDelivered.complete(Unit)
        }
        val failing = timeline.observeEach {
            healthyDelivered.await()
            throw failure
        }
        try {
            timeline.emit(event(1))
            val actual = assertFailsWith<IllegalStateException> { timeline.advance(startTime + 2.seconds) }
            assertFailure(failure, actual)
            timeline.emit(event(2))
            timeline.finish()
            healthy.collect(startTime + 2.seconds)
            assertEquals(listOf(1, 2), received)
            assertEquals(startTime + 2.seconds, timeline.time.value)
        } finally {
            healthy.close()
            failing.close()
            timeline.close()
        }
    }

    @Test
    fun theDefaultManualBufferRetainsMoreThanASmallFixedCapacity() = contractTest {
        val timeline = shared()
        try {
            for (second in 1..256) timeline.emit(event(second))
            timeline.finish()
            val received = mutableListOf<Int>()
            val observer = timeline.observeEach { received.add(it.value) }
            try {
                observer.collect(startTime + 256.seconds)
                assertEquals((1..256).toList(), received)
            } finally {
                observer.close()
            }
        } finally {
            timeline.close()
        }
    }

    @Test
    fun interruptReplacesAProducerWaitingForCapacityAndPreservesItsPrefix() = contractTest {
        for (capacity in listOf(0, 1, 2)) {
            val prepared = CompletableDeferred<Unit>()
            val timeline = generating(bufferSize = capacity, lookahead = 100.seconds) { origin ->
                if (origin.value == 0) {
                    for (second in 1..maxOf(1, capacity)) emit(event(second))
                    prepared.complete(Unit)
                    emit(event(20, 200))
                } else {
                    emit(event(20, 201))
                }
            }
            val fastEvents = mutableListOf<Int>()
            val slowEvents = mutableListOf<Int>()
            val fast = timeline.observeEach { fastEvents.add(it.value) }
            val slow = timeline.observeEach { slowEvents.add(it.value) }
            try {
                val boundary = maxOf(1, capacity)
                val first = async { fast.collect(startTime + boundary.seconds) }
                if (capacity == 0) {
                    runCurrent()
                    assertEquals(listOf(1), fastEvents)
                    timeline.interrupt(event(boundary))
                } else {
                    prepared.await()
                    first.await()
                    timeline.interrupt(event(boundary))
                }
                val second = async { slow.collect(startTime + boundary.seconds) }
                first.await()
                second.await()
                val fastCollection = async { fast.collect(startTime + 20.seconds) }
                val slowCollection = async { slow.collect(startTime + 20.seconds) }
                fastCollection.await()
                slowCollection.await()
                val expected = (1..boundary).toList() + 201
                assertEquals(expected, fastEvents)
                assertEquals(expected, slowEvents)
            } finally {
                fast.close()
                slow.close()
                timeline.close()
            }
        }
    }
}
