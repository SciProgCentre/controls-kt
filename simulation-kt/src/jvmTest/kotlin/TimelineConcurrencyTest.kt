package space.kscience.simulation

import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimelineConcurrencyTest {
    private val startTime = Instant.parse("2020-01-01T00:00:00Z")

    private fun event(second: Int, value: Int = second): TimelineEvent<Int> =
        TimelineEvent(startTime + second.seconds, value)

    private fun assertClosedOrSuccessful(result: Result<Unit>) {
        result.exceptionOrNull()?.let { error ->
            assertTrue(
                error is CancellationException ||
                    error is IllegalStateException && error.message.orEmpty().contains("closed"),
                "Unexpected failure during close: $error",
            )
        }
    }

    @Test
    fun concurrentRegistrationRequestsAndCloseLeaveNoWaiters() = runTest(timeout = 30.seconds) {
        withContext(Dispatchers.Default) {
            repeat(24) {
                val timeline = SharedTimeline(startTime, TimelineEvent<Int>::time, 2, coroutineContext)
                val firstDelivered = CompletableDeferred<Unit>()
                val releaseCallback = CompletableDeferred<Unit>()
                val received = ConcurrentLinkedQueue<Int>()
                val observer = timeline.observeEach {
                    received.add(it.value)
                    if (it.value == 1) {
                        firstDelivered.complete(Unit)
                        releaseCallback.await()
                    }
                }
                try {
                    timeline.emit(event(1))
                    timeline.emit(event(2))
                    timeline.finish()
                    val originalRequest = async { runCatching { observer.collect(event(2).time) } }
                    firstDelivered.await()

                    val start = CompletableDeferred<Unit>()
                    val ready = Channel<Unit>(Channel.UNLIMITED)
                    val registrations = List(4) {
                        async {
                            ready.send(Unit)
                            start.await()
                            var concurrentObserver: TimelineObserver? = null
                            val laterEvents = ConcurrentLinkedQueue<Int>()
                            val result = try {
                                runCatching {
                                    concurrentObserver = timeline.observeEach { laterEvents.add(it.value) }
                                    concurrentObserver.collect(event(2).time)
                                }
                            } finally {
                                concurrentObserver?.close()
                            }
                            assertTrue(laterEvents.all { it == 2 }, "A late observer received protected history")
                            result
                        }
                    }
                    val nextRequest = async {
                        ready.send(Unit)
                        start.await()
                        runCatching { observer.collect(event(2).time) }
                    }
                    val closing = async {
                        ready.send(Unit)
                        start.await()
                        timeline.close()
                        timeline.close()
                    }
                    repeat(6) { ready.receive() }
                    start.complete(Unit)
                    releaseCallback.complete(Unit)

                    closing.await()
                    (registrations.awaitAll() + originalRequest.await() + nextRequest.await())
                        .forEach(::assertClosedOrSuccessful)
                    assertTrue(received.toList() in listOf(listOf(1), listOf(1, 2)))
                    assertFailsWith<IllegalStateException> { timeline.observeEach { } }
                    assertTrue(coroutineContext[Job]!!.isActive)
                } finally {
                    releaseCallback.complete(Unit)
                    observer.close()
                    timeline.close()
                }
            }
        }
    }

    @Test
    fun concurrentRestartsPreserveCompletedRangesAndLaggingReaders() = runTest(timeout = 30.seconds) {
        withContext(Dispatchers.Default) {
            repeat(24) {
                val originsStarted = Channel<Int>(Channel.UNLIMITED)
                val releaseGeneration = CompletableDeferred<Unit>()
                val timeline = GeneratingTimeline(
                    origin = event(0, 0),
                    lookaheadInterval = Duration.ZERO,
                    timeOf = TimelineEvent<Int>::time,
                    coroutineContext = coroutineContext,
                    bufferSize = 2,
                ) { origin ->
                    if (origin.value == 0) {
                        emit(event(1))
                        emit(event(10))
                    } else {
                        originsStarted.send(origin.value)
                        releaseGeneration.await()
                        emit(event(3, origin.value))
                    }
                }
                val fastEvents = ConcurrentLinkedQueue<Int>()
                val slowEvents = ConcurrentLinkedQueue<Int>()
                val fast = timeline.observeEach { fastEvents.add(it.value) }
                val slow = timeline.observeEach { slowEvents.add(it.value) }
                try {
                    fast.collect(event(2).time)
                    assertEquals(listOf(1), fastEvents.toList())
                    assertEquals(event(1).time, timeline.time.value)

                    val originA = event(2, 10)
                    val originB = event(2, 20)
                    timeline.interrupt(originA)
                    val request = async { fast.collect(event(4).time) }
                    assertEquals(10, originsStarted.receive())
                    timeline.interrupt(originB)
                    assertEquals(20, originsStarted.receive())
                    timeline.interrupt(originA)
                    assertEquals(10, originsStarted.receive())

                    val ready = Channel<Unit>(Channel.UNLIMITED)
                    val start = CompletableDeferred<Unit>()
                    val restarts = listOf(originB, originA).map { origin ->
                        async {
                            ready.send(Unit)
                            start.await()
                            timeline.interrupt(origin)
                        }
                    }
                    repeat(2) { ready.receive() }
                    start.complete(Unit)
                    restarts.awaitAll()
                    releaseGeneration.complete(Unit)
                    request.await()
                    slow.collect(event(4).time)

                    val result = fastEvents.toList()
                    assertEquals(2, result.size)
                    assertEquals(1, result.first())
                    assertTrue(result.last() in setOf(10, 20))
                    assertEquals(result, slowEvents.toList())
                    assertEquals(event(3).time, timeline.time.value)
                    assertFailsWith<IllegalStateException> { timeline.interrupt(event(3, 30)) }
                    timeline.interrupt(event(4, 30))
                } finally {
                    releaseGeneration.complete(Unit)
                    fast.close()
                    slow.close()
                    timeline.close()
                }
            }
        }
    }

    @Test
    fun sourceCancellationFailsItsRequestAndAllowsRestart() = runTest(timeout = 30.seconds) {
        withContext(Dispatchers.Default) {
            repeat(20) { iteration ->
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val failure = CancellationException("source stopped $iteration")
                val timeline = GeneratingTimeline(
                    origin = event(0, 0),
                    lookaheadInterval = Duration.ZERO,
                    timeOf = TimelineEvent<Int>::time,
                    coroutineContext = coroutineContext,
                ) { origin ->
                    if (origin.value == 0) {
                        started.complete(Unit)
                        release.await()
                        throw failure
                    }
                    emit(event(1))
                }
                val received = ConcurrentLinkedQueue<Int>()
                val observer = timeline.observeEach { received.add(it.value) }
                try {
                    val request = async { runCatching { observer.collect(event(1).time) } }
                    started.await()
                    release.complete(Unit)
                    val actual = assertIs<CancellationException>(request.await().exceptionOrNull())
                    assertEquals(failure.message, actual.message)
                    assertTrue(coroutineContext[Job]!!.isActive)

                    timeline.interrupt(event(0, 1))
                    observer.collect(event(1).time)
                    assertEquals(listOf(1), received.toList())
                } finally {
                    release.complete(Unit)
                    observer.close()
                    timeline.close()
                }
            }
        }
    }
}
