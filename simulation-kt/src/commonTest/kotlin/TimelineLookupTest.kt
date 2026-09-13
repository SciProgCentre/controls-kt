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
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimelineLookupTest {
    private val start = Instant.fromEpochSeconds(0)
    private fun event(second: Int, value: Int = second): TimelineEvent<Int> =
        TimelineEvent(start + second.seconds, value)

    private suspend fun TimelineState<TimelineEvent<Int>>.expect(
        reader: TimelineState.Reader,
        through: Int,
        expected: List<TimelineEvent<Int>>,
    ) {
        startRequest(reader, start + through.seconds)
        for (value in expected) {
            assertEquals(value, assertIs<TimelineState.Step.Event<TimelineEvent<Int>>>(next(reader)).value)
        }
        assertNull(assertIs<TimelineState.Step.Completed>(next(reader)).failure)
    }

    @Test
    fun aFastReaderDoesNotLoseEventsPinnedByAnIdleReader() = runTest(timeout = 10.seconds) {
        val state = TimelineState(start, TimelineEvent<Int>::time, Channel.UNLIMITED)
        try {
            val idle = state.register()
            val fast = state.register()
            val events = (1..256).map { event(it) }
            events.forEach { state.publish(it) }
            state.finish()

            state.expect(fast, 256, events)
            assertEquals(start, idle.time.value)
            state.expect(idle, 256, events)
        } finally {
            state.close()
        }
    }

    @Test
    fun aCancelledPendingPublicationLeavesNoHoleInReaderDelivery() = runTest(timeout = 10.seconds) {
        val state = TimelineState(start, TimelineEvent<Int>::time, 2)
        try {
            val slow = state.register()
            val fast = state.register()
            state.publish(event(1))
            state.publish(event(2))
            state.expect(fast, 1, listOf(event(1)))

            val pending = async(start = CoroutineStart.UNDISPATCHED) { state.publish(event(3)) }
            assertFalse(pending.isCompleted)
            pending.cancelAndJoin()

            state.expect(slow, 1, listOf(event(1)))
            state.publish(event(4))
            state.finish()
            state.expect(fast, 4, listOf(event(2), event(4)))
            state.expect(slow, 4, listOf(event(2), event(4)))
        } finally {
            state.close()
        }
    }

    @Test
    fun restartPreservesTheDeliveredPrefixAndReplacesTheUnreadTail() = runTest(timeout = 10.seconds) {
        val state = TimelineState(start, TimelineEvent<Int>::time, Channel.UNLIMITED)
        try {
            val slow = state.register()
            val fast = state.register()
            (1..3).forEach { state.publish(event(it)) }
            state.expect(fast, 1, listOf(event(1)))

            state.restart(event(1, 10))
            state.publish(event(2, 20))
            state.publish(event(3, 30))
            state.finish()

            state.expect(fast, 3, listOf(event(2, 20), event(3, 30)))
            state.expect(slow, 3, listOf(event(1), event(2, 20), event(3, 30)))
        } finally {
            state.close()
        }
    }
}
