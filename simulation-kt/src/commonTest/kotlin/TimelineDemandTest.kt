package space.kscience.simulation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimelineDemandTest {
    private val start = Instant.fromEpochSeconds(0)
    private fun event(second: Int) = TimelineEvent(start + second.seconds, second)

    @Test
    fun generatedEventsUseActiveDemandAtTheRequestedBoundary() = runTest(timeout = 10.seconds) {
        val state = TimelineState(start, TimelineEvent<Int>::time, Channel.UNLIMITED)
        state.initializeOrigin(event(0), 10.seconds)
        try {
            state.register()
            val closed = state.register()
            state.startRequest(closed, start + 100.seconds)
            state.close(closed)
            val shorter = state.register()
            state.startRequest(shorter, start + 8.seconds)
            val reader = state.register()
            val request = state.startRequest(reader, start + 20.seconds)

            state.publish(event(20), generated = true)
            assertEquals(event(20), state.lastEvent)
            state.cancelRequest(reader, request)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { state.publish(event(21), generated = true) }
            assertFalse(pending.isCompleted)

            state.startRequest(reader, start + 21.seconds)
            pending.await()
            assertEquals(event(21), state.lastEvent)
        } finally {
            state.close()
        }
    }

    @Test
    fun lookaheadIncludesItsBoundaryAndMovesOnlyWithDelivery() = runTest(timeout = 10.seconds) {
        val state = TimelineState(start, TimelineEvent<Int>::time, Channel.UNLIMITED)
        state.initializeOrigin(event(0), 10.seconds)
        try {
            state.publish(event(10), generated = true)
            val pending = async(start = CoroutineStart.UNDISPATCHED) { state.publish(event(11), generated = true) }
            assertFalse(pending.isCompleted)
            val reader = state.register()
            val request = state.startRequest(reader, start + 11.seconds)
            pending.await()

            assertEquals(event(10), assertIs<TimelineState.Step.Event<TimelineEvent<Int>>>(state.next(reader)).value)
            state.cancelRequest(reader, request)
            state.publish(event(20), generated = true)
            assertEquals(event(20), state.lastEvent)

            val outside = async(start = CoroutineStart.UNDISPATCHED) { state.publish(event(21), generated = true) }
            assertFalse(outside.isCompleted)
            outside.cancelAndJoin()
        } finally {
            state.close()
        }
    }
}
