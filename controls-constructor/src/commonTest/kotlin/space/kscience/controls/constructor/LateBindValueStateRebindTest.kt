@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kscience.controls.constructor

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.time.ValueWithTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LateBindValueStateRebindTest {
    private fun sample(value: Int?, second: Long) = ValueWithTime(value, Instant.fromEpochSeconds(second))

    private class TestState(
        private val equalityKey: Int,
        initial: ValueWithTime<Int?>,
    ) : ValueState<Int?> {
        val samples = MutableStateFlow(initial)
        var subscriptions = 0
            private set

        override val valueWithTime: ValueWithTime<Int?> get() = samples.value

        override fun subscribeWithTime(): Flow<ValueWithTime<Int?>> = flow {
            subscriptions++
            try {
                emitAll(samples)
            } finally {
                subscriptions--
            }
        }

        override fun equals(other: Any?): Boolean = other is TestState && equalityKey == other.equalityKey

        override fun hashCode(): Int = equalityKey

        override fun toString(): String = "TestState($equalityKey)"
    }

    @Test
    fun rebindSetsAnUnboundStateAndBindRemainsSingleAssignment() {
        val state = LateBindValueState<Int?>(42, Instant.fromEpochSeconds(1))
        val first = TestState(1, sample(null, 2))
        val second = TestState(2, sample(7, 3))

        assertFalse(state.isBound)
        assertEquals(sample(42, 1), state.valueWithTime)
        state.rebind(first)
        assertTrue(state.isBound)
        assertNull(state.value)
        assertEquals(sample(null, 2), state.valueWithTime)
        assertFailsWith<IllegalStateException> { state.bind(second) }

        state.rebind(second)
        assertEquals(7, state.value)
        assertEquals(sample(7, 3), state.valueWithTime)
        assertFailsWith<IllegalStateException> { state.bind(first) }
    }

    @Test
    fun liveSubscriptionsSwitchSourcesWithoutChangingSampleTimes() = runTest {
        val state = LateBindValueState<Int?>(0, Instant.fromEpochSeconds(1))
        val first = TestState(1, sample(2, 20))
        val second = TestState(2, sample(4, 40))
        val values = mutableListOf<Int?>()
        val samples = mutableListOf<ValueWithTime<Int?>>()
        val valueJob = backgroundScope.launch { state.subscribe().toList(values) }
        val sampleJob = backgroundScope.launch { state.subscribeWithTime().toList(samples) }
        runCurrent()
        assertEquals(listOf<Int?>(0), values)
        assertEquals(listOf(sample(0, 1)), samples)

        state.bind(first)
        runCurrent()
        assertEquals(2, first.subscriptions)
        first.samples.value = sample(3, 30)
        runCurrent()

        state.rebind(second)
        assertEquals(sample(4, 40), state.valueWithTime)
        runCurrent()
        assertEquals(0, first.subscriptions)
        assertEquals(2, second.subscriptions)

        first.samples.value = sample(99, 99)
        second.samples.value = sample(null, 50)
        runCurrent()
        assertEquals(listOf(0, 2, 3, 4, null), values)
        assertEquals(listOf(sample(0, 1), sample(2, 20), sample(3, 30), sample(4, 40), sample(null, 50)), samples)

        valueJob.cancelAndJoin()
        sampleJob.cancelAndJoin()
        assertEquals(0, second.subscriptions)
    }

    @Test
    fun equalTargetsStillReplaceTheSubscription() = runTest {
        val first = TestState(7, sample(1, 10))
        val second = TestState(7, sample(2, 20))
        assertEquals(first, second)
        val state = LateBindValueState<Int?>(0)
        state.bind(first)
        val samples = mutableListOf<ValueWithTime<Int?>>()
        val job = backgroundScope.launch { state.subscribeWithTime().toList(samples) }
        runCurrent()

        state.rebind(second)
        runCurrent()
        assertEquals(0, first.subscriptions)
        assertEquals(1, second.subscriptions)
        first.samples.value = sample(99, 99)
        second.samples.value = sample(3, 30)
        runCurrent()
        assertEquals(listOf(sample(1, 10), sample(2, 20), sample(3, 30)), samples)

        job.cancelAndJoin()
        assertEquals(0, second.subscriptions)
    }
}
