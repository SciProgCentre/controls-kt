package space.kscience.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineLifecycleTest {
    private val start = Instant.parse("2020-01-01T00:00:00Z")

    @Test
    fun cancellingARequestPublishesItsDeliveredTime() = runTest {
        val event = TimelineEvent(start + 20.seconds, 20)
        val state = TimelineState(start, TimelineEvent<Int>::time, 1)
        val reader = state.register()
        val request = state.startRequest(reader, event.time)
        state.publish(event)
        val cancelledTime = CompletableDeferred<Instant>()
        val cancellation = launch(UnconfinedTestDispatcher(testScheduler)) {
            state.changes.drop(1).take(1).collect {
                assertEquals(event.time, reader.deliveredTime)
                assertEquals(start, reader.time.value)
                state.cancelRequest(reader, request)
                cancelledTime.complete(reader.time.value)
            }
        }
        try {
            state.next(reader)
            assertEquals(event.time, cancelledTime.await())
        } finally {
            cancellation.cancel()
            state.close()
        }
    }

    @Test
    fun closingDuringRegistrationLeavesNoWaitingRequestOrOwnedJob() = runTest(timeout = 10.seconds) {
        for (cancelOwner in listOf(false, true)) {
            val borrowed = SupervisorJob(coroutineContext[Job])
            val timeline = SharedTimeline(start, TimelineEvent<Int>::time, 0, coroutineContext + borrowed)
            val owner = borrowed.children.single()
            var closedDuringRegistration = false
            // register signals after adding the reader, before observe launches its worker.
            val closing = launch(UnconfinedTestDispatcher(testScheduler)) {
                timeline.state.changes.drop(1).take(1).collect {
                    closedDuringRegistration = true
                    if (cancelOwner) owner.cancel() else timeline.close()
                }
            }
            var observer: TimelineObserver? = null
            try {
                val registration = runCatching {
                    timeline.observeEach { error("A closed empty timeline delivered an event") }
                }
                observer = registration.getOrNull()
                closing.join()
                assertTrue(closedDuringRegistration)

                val failure = observer?.let { runCatching { it.collect(start) }.exceptionOrNull() }
                    ?: registration.exceptionOrNull()
                assertTrue(
                    failure is CancellationException ||
                        failure is IllegalStateException && failure.message.orEmpty().contains("closed"),
                    "Registration or its request must fail after close: $failure",
                )
                owner.join()
                assertTrue(borrowed.isActive)
                assertFalse(borrowed.children.any(), "The closed timeline retained an owned job")
            } finally {
                observer?.close()
                closing.cancelAndJoin()
                timeline.close()
                borrowed.cancelAndJoin()
            }
        }
    }
}
