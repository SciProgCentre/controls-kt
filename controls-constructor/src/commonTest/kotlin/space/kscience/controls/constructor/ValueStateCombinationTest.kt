package space.kscience.controls.constructor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.time.ValueWithTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ValueStateCombinationTest {
    private class ReadingState : ValueState<Int> {
        var reads = 0

        override val valueWithTime: ValueWithTime<Int>
            get() {
                reads++
                return ValueWithTime(reads, Instant.fromEpochSeconds(reads.toLong()))
            }

        override fun subscribeWithTime(): Flow<ValueWithTime<Int>> = emptyFlow()

        override fun toString(): String = "ReadingState($reads)"
    }

    private fun assertSingleRead(result: ValueState<Int>, vararg inputs: ReadingState) {
        inputs.forEach { it.reads = 0 }
        assertEquals(ValueWithTime(inputs.size, Instant.fromEpochSeconds(1)), result.valueWithTime)
        inputs.forEach { assertEquals(1, it.reads) }
    }

    @Test
    fun testMapReadsOnce() {
        val input = ReadingState()
        assertSingleRead(ValueState.map(input) { it }, input)
    }

    @Test
    fun testScopedMapReadsOnce() = runTest {
        val input = ReadingState()
        assertSingleRead(ValueState.map(backgroundScope, input) { it }, input)
    }

    @Test
    fun testTwoStateCombinationReadsOnce() = runTest {
        val first = ReadingState()
        val second = ReadingState()
        assertSingleRead(ValueState.combine(backgroundScope, first, second) { a, b -> a + b }, first, second)
    }

    @Test
    fun testThreeStateCombinationReadsOnce() = runTest {
        val first = ReadingState()
        val second = ReadingState()
        val third = ReadingState()
        val combined = ValueState.combine(backgroundScope, first, second, third) { a, b, c -> a + b + c }
        assertSingleRead(combined, first, second, third)
    }

    @Test
    fun testFourStateCombinationReadsOnce() = runTest {
        val first = ReadingState()
        val second = ReadingState()
        val third = ReadingState()
        val fourth = ReadingState()
        val combined = ValueState.combine(backgroundScope, first, second, third, fourth) { a, b, c, d -> a + b + c + d }
        assertSingleRead(combined, first, second, third, fourth)
    }

    @Test
    fun testCollectionCombinationReadsOnce() = runTest {
        val first = ReadingState()
        val second = ReadingState()
        assertSingleRead(ValueState.combine(backgroundScope, listOf(first, second)) { it.sum() }, first, second)
    }

    @Test
    fun testMapCombinationReadsOnce() = runTest {
        val first = ReadingState()
        val second = ReadingState()
        val combined = ValueState.combine(backgroundScope, mapOf("left" to first, "right" to second)) { it.values.sum() }
        assertSingleRead(combined, first, second)
    }

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
