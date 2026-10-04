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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// LLM generated code: Tests for numerical correctness of differentiate and integrate
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
    fun testDifferentiateNumericalCorrectness() = runTest(timeout = 5.seconds) {
        val t0 = Instant.fromEpochSeconds(10_000)
        // Source initialized with a valid timestamp
        val source = CustomTimedState(ValueWithTime(10.0, t0))
        val diffState = source.differentiate(backgroundScope)
        assertEquals(ValueWithTime(0.0, t0), diffState.valueWithTime)

        // Linear slope: rate of change = +3.5 units/sec
        source.emit(13.5, t0 + 1.seconds)
        assertEquals(3.5, diffState.subscribeWithTime().first { it.time == t0 + 1.seconds }.value, 1e-9)

        // Non-uniform time step (2.0 sec, delta = -7.0 -> slope = -3.5)
        source.emit(6.5, t0 + 3.seconds)
        assertEquals(-3.5, diffState.subscribeWithTime().first { it.time == t0 + 3.seconds }.value, 1e-9)

        // Sub-second time step (500 ms, delta = 2.0 -> slope = +4.0)
        source.emit(8.5, t0 + 3.seconds + 500.milliseconds)
        assertEquals(4.0, diffState.subscribeWithTime().first { it.time == t0 + 3.seconds + 500.milliseconds }.value, 1e-9)

        // Quadratic function y = t^2 evaluated at t = 4, 5, 7 relative to t0
        // At t = 4s: y = 16.0 -> diff = (16.0 - 8.5) / 0.5s = 15.0
        source.emit(16.0, t0 + 4.seconds)
        assertEquals(15.0, diffState.subscribeWithTime().first { it.time == t0 + 4.seconds }.value, 1e-9)

        // At t = 5s: y = 25.0 -> diff = (25.0 - 16.0) / 1.0s = 9.0
        source.emit(25.0, t0 + 5.seconds)
        assertEquals(9.0, diffState.subscribeWithTime().first { it.time == t0 + 5.seconds }.value, 1e-9)

        // Constant segment: slope = 0.0
        source.emit(25.0, t0 + 8.seconds)
        assertEquals(0.0, diffState.subscribeWithTime().first { it.time == t0 + 8.seconds }.value, 1e-9)
    }

    @Test
    fun testIntegrateCountsStartingValue() = runTest(timeout = 5.seconds) {
        val state = ValueState(25.0).integrate(10.seconds, backgroundScope)
        val expected = ValueWithTime(25.0, Instant.DISTANT_PAST)

        assertEquals(expected, state.valueWithTime)
        runCurrent()
        assertEquals(expected, state.valueWithTime)
    }

    @Test
    fun testIntegrateTrapezoidNumericalCorrectness() = runTest(timeout = 5.seconds) {
        val t0 = Instant.fromEpochSeconds(1_000)
        val source = CustomTimedState(ValueWithTime(10.0, t0))
        // Window of 100 seconds to cover all samples
        val integralState = source.integrate(100.seconds, backgroundScope)
        assertEquals(ValueWithTime(0.0, t0), integralState.valueWithTime)

        // Step 1: Constant signal y = 10.0 over 2 seconds -> trapezoid = (10+10)/2 * 2 = 20.0
        source.emit(10.0, t0 + 2.seconds)
        val sample1 = integralState.subscribeWithTime().first { it.time == t0 + 2.seconds }
        assertEquals(20.0, sample1.value, 1e-9)

        // Step 2: Linear ramp from y = 10.0 to y = 20.0 over 3 seconds -> area = (10+20)/2 * 3 = 45.0
        // Cumulative integral = 20.0 + 45.0 = 65.0
        source.emit(20.0, t0 + 5.seconds)
        val sample2 = integralState.subscribeWithTime().first { it.time == t0 + 5.seconds }
        assertEquals(65.0, sample2.value, 1e-9)

        // Step 3: Linear ramp from y = 20.0 to y = 0.0 over 1 second -> area = (20+0)/2 * 1 = 10.0
        // Cumulative integral = 65.0 + 10.0 = 75.0
        source.emit(0.0, t0 + 6.seconds)
        val sample3 = integralState.subscribeWithTime().first { it.time == t0 + 6.seconds }
        assertEquals(75.0, sample3.value, 1e-9)

        // Step 4: Sub-second duration (250 ms) from y = 0.0 to y = 4.0 -> area = (0+4)/2 * 0.25 = 0.5
        // Cumulative integral = 75.0 + 0.5 = 75.5
        source.emit(4.0, t0 + 6.seconds + 250.milliseconds)
        val sample4 = integralState.subscribeWithTime().first { it.time == t0 + 6.seconds + 250.milliseconds }
        assertEquals(75.5, sample4.value, 1e-9)
    }

    @Test
    fun testIntegrateRollingWindowExpiryAndRepublish() = runTest(timeout = 5.seconds) {
        val t0 = Instant.fromEpochSeconds(5_000)
        val source = CustomTimedState(ValueWithTime(10.0, t0))
        val window = 5.seconds
        val integralState = source.integrate(window, backgroundScope)

        // t = t0 + 2s (y=20): history = [(10, t0), (20, t0+2)], area = (10+20)/2 * 2 = 30.0
        source.emit(20.0, t0 + 2.seconds)
        assertEquals(30.0, integralState.subscribeWithTime().first { it.time == t0 + 2.seconds }.value, 1e-9)

        // t = t0 + 4s (y=30): history = [(10, t0), (20, t0+2), (30, t0+4)], total area = 30 + (20+30)/2 * 2 = 80.0
        source.emit(30.0, t0 + 4.seconds)
        assertEquals(80.0, integralState.subscribeWithTime().first { it.time == t0 + 4.seconds }.value, 1e-9)

        // t = t0 + 6s (y=40): window is [t0+1, t0+6], t0 point expired -> history = [(20, t0+2), (30, t0+4), (40, t0+6)]
        // Area = (20+30)/2 * 2 + (30+40)/2 * 2 = 50 + 70 = 120.0
        source.emit(40.0, t0 + 6.seconds)
        assertEquals(120.0, integralState.subscribeWithTime().first { it.time == t0 + 6.seconds }.value, 1e-9)

        // Out-of-order sample at t = t0 + 5s: ignored for addition (time <= t0+6s), trims window [t0, t0+5]
        // History retains [(20, t0+2), (30, t0+4), (40, t0+6)], area = 120.0, published at t0 + 5s
        source.emit(99.0, t0 + 5.seconds)
        val sampleOutOfOrder = integralState.subscribeWithTime().first { it.time == t0 + 5.seconds }
        assertEquals(120.0, sampleOutOfOrder.value, 1e-9)

        // Null sample at t = t0 + 10s: window is [t0+5, t0+10] -> only (40, t0+6) remains -> single point = 0.0
        source.emit(null, t0 + 10.seconds)
        val sampleNull = integralState.subscribeWithTime().first { it.time == t0 + 10.seconds }
        assertEquals(0.0, sampleNull.value, 1e-9)
    }

    @Test
    fun testIntegrateAndDifferentiateRoundTrip() = runTest(timeout = 5.seconds) {
        val t0 = Instant.fromEpochSeconds(20_000)
        val rate = 5.0
        // Signal with constant value = 5.0
        val source = CustomTimedState(ValueWithTime(rate, t0))
        val integrated = source.integrate(100.seconds, backgroundScope)
        val differentiated = integrated.differentiate(backgroundScope)

        // Emit constant samples at 1s, 2s, 3s, 4s
        for (i in 1..4) {
            val t = t0 + i.seconds
            source.emit(rate, t)
            val diff = differentiated.subscribeWithTime().first { it.time == t }
            // Differentiating the integral of constant rate must recover the rate
            assertEquals(rate, diff.value, 1e-9)
        }
    }
}
