package space.kscience.controls.constructor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.expressions.differentiate
import space.kscience.controls.constructor.expressions.integrate
import space.kscience.controls.time.ValueWithTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NumericStateTest {

    private class CustomTimedState(initial: ValueWithTime<Double?>) : ValueState<Double?> {
        private val state = MutableStateFlow(initial)

        override val valueWithTime: ValueWithTime<Double?> get() = state.value

        override fun subscribeWithTime(): Flow<ValueWithTime<Double?>> = state

        suspend fun emit(value: Double?, time: Instant) {
            state.emit(ValueWithTime(value, time))
        }

        override fun toString(): String = "CustomTimedState(${state.value})"
    }

    @Test
    fun testDifferentiateTimedSamples() = runTest(timeout = 5.seconds) {
        val source = CustomTimedState(ValueWithTime(25.0, Instant.DISTANT_PAST))
        val state = source.differentiate(backgroundScope)
        val initial = ValueWithTime(0.0, Instant.DISTANT_PAST)
        val t1 = Instant.fromEpochSeconds(1_000)

        assertEquals(initial, state.valueWithTime)
        assertSame(source, assertIs<ValueStateWithDependencies<Double>>(state).dependencies.single())
        runCurrent()

        source.emit(30.0, t1)
        runCurrent()
        assertEquals(initial, state.valueWithTime)

        source.emit(32.0, t1 + 1.seconds)
        val firstDerivative = ValueWithTime(2.0, t1 + 1.seconds)
        assertEquals(firstDerivative, state.subscribeWithTime().first { it.time == firstDerivative.time })

        source.emit(null, t1 + 2.seconds)
        runCurrent()
        assertEquals(firstDerivative, state.valueWithTime)

        source.emit(36.0, t1 + 3.seconds)
        val nextDerivative = ValueWithTime(2.0, t1 + 3.seconds)
        assertEquals(nextDerivative, state.subscribeWithTime().first { it.time == nextDerivative.time })

        source.emit(40.0, t1 + 2.seconds)
        runCurrent()
        assertEquals(nextDerivative, state.valueWithTime)
    }

    @Test
    fun testIntegrateCountsStartingValue() = runTest(timeout = 5.seconds) {
        val state = ValueState(25.0).integrate(10.seconds, backgroundScope)
        val expected = ValueWithTime(25.0, Instant.DISTANT_PAST)

        assertEquals(expected, state.valueWithTime)
        runCurrent()
        assertEquals(expected, state.valueWithTime)
    }
}
