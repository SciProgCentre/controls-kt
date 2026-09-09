package space.kscience.controls.constructor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.time.ValueWithTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ValueStateCombinationTest {
    private class TimedState(initial: ValueWithTime<Int>) : ValueState<Int> {
        private val samples = MutableStateFlow(initial)
        private var current = initial

        override val valueWithTime: ValueWithTime<Int> get() = current

        override fun subscribeWithTime(): Flow<ValueWithTime<Int>> = samples

        suspend fun emit(sample: ValueWithTime<Int>, current: ValueWithTime<Int> = sample) {
            this.current = current
            samples.emit(sample)
        }

        override fun toString(): String = "TimedState($current)"
    }

    @Test
    fun testTimedCollectionCombination() = runTest {
        val first = TimedState(ValueWithTime(2, Instant.fromEpochSeconds(100)))
        val second = TimedState(ValueWithTime(3, Instant.fromEpochSeconds(200)))
        val combined = ValueState.combine(backgroundScope, listOf(first, second)) { it.sum() }
        val results = Channel<ValueWithTime<Int>>()
        val collector = launch { combined.subscribeWithTime().collect { results.send(it) } }

        assertEquals(ValueWithTime(5, Instant.fromEpochSeconds(200)), results.receive())
        assertEquals(listOf(first, second), combined.dependencies)

        first.emit(
            ValueWithTime(4, Instant.fromEpochSeconds(300)),
            ValueWithTime(9, Instant.fromEpochSeconds(900))
        )
        assertEquals(ValueWithTime(7, Instant.fromEpochSeconds(300)), results.receive())
        collector.cancel()
    }

    @Test
    fun testTimedMapCombination() = runTest {
        val first = TimedState(ValueWithTime(2, Instant.fromEpochSeconds(100)))
        val second = TimedState(ValueWithTime(3, Instant.fromEpochSeconds(200)))
        val combined = ValueState.combine(backgroundScope, linkedMapOf("left" to first, "right" to second)) {
            assertEquals(setOf("left", "right"), it.keys)
            it.getValue("left") * 10 + it.getValue("right")
        }
        val results = Channel<ValueWithTime<Int>>()
        val collector = launch { combined.subscribeWithTime().collect { results.send(it) } }

        assertEquals(ValueWithTime(23, Instant.fromEpochSeconds(200)), results.receive())
        assertEquals(listOf(first, second), combined.dependencies.toList())

        first.emit(
            ValueWithTime(4, Instant.fromEpochSeconds(300)),
            ValueWithTime(9, Instant.fromEpochSeconds(900))
        )
        assertEquals(ValueWithTime(43, Instant.fromEpochSeconds(300)), results.receive())
        collector.cancel()
    }
}
