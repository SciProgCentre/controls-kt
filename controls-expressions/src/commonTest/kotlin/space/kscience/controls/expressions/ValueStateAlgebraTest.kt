@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kscience.controls.expressions

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.time.ValueWithTime
import space.kscience.kmath.expressions.Symbol
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/*
 * LLM generated code: Tests for ValueStateAlgebra verifying static expression evaluation,
 * reactive updates on source state changes, and flow subscription notifications.
 */
class ValueStateAlgebraTest {

    @Test
    fun testStaticComputation() {
        val x = ValueState(PI / 6)
        val y = ValueState(2.0)
        val bindings = mapOf(
            Symbol("x") to x,
            Symbol("y") to y
        )

        assertEquals(0.0, ValueStateAlgebra.interpret("0.0", bindings).value)

        // Arithmetic, brackets, and trigonometry: sin(x) * y + (4.0 / 2.0) - cos(0.0)
        // sin(PI/6) = 0.5; 0.5 * 2.0 = 1.0; 1.0 + (4.0 / 2.0) = 3.0; 3.0 - 1.0 = 2.0
        val expr = ValueStateAlgebra.interpret("sin(x) * y + (4.0 / 2.0) - cos(0.0)", bindings)
        assertEquals(2.0, expr.value!!, 1e-9)

        // Complex arithmetic with brackets and powers
        // ((2.0 + 3.0) * (10.0 - 4.0)) / (1.0 + 2.0) = (5.0 * 6.0) / 3.0 = 10.0
        val arithmeticExpr = ValueStateAlgebra.interpret("((2.0 + 3.0) * (10.0 - 4.0)) / (1.0 + 2.0)", emptyMap())
        assertEquals(10.0, arithmeticExpr.value!!, 1e-9)

        // Trigonometric identities and functions: atan(1.0) * 4.0 == PI, sin(PI/2) == 1.0, cos(PI) == -1.0
        val piState = ValueState(PI)
        val trigExpr =
            ValueStateAlgebra.interpret("sin(pi / 2.0) + cos(pi) + atan(1.0) * 4.0", mapOf(Symbol("pi") to piState))
        assertEquals(PI, trigExpr.value!!, 1e-9)
    }

    @Test
    fun testValueChangeWhenInitialValueChanges() {
        val a = MutableValueState(2.0)
        val b = MutableValueState(3.0)
        val bindings = mapOf(
            Symbol("a") to a,
            Symbol("b") to b
        )

        // a * a + 2.0 * a * b + b * b = (a + b)^2
        val expr = ValueStateAlgebra.interpret("a * a + 2.0 * a * b + b * b", bindings)

        // Initial: (2 + 3)^2 = 25
        assertEquals(25.0, expr.value!!, 1e-9)

        // Change 'a' to 4.0: (4 + 3)^2 = 49
        a.value = 4.0
        assertEquals(49.0, expr.value!!, 1e-9)

        // Change 'b' to 1.0: (4 + 1)^2 = 25
        b.value = 1.0
        assertEquals(25.0, expr.value!!, 1e-9)

        // Change both: a = 1.5, b = 2.5: (1.5 + 2.5)^2 = 16
        a.value = 1.5
        b.value = 2.5
        assertEquals(16.0, expr.value!!, 1e-9)
    }

    @Test
    fun testSubscriptionSendsNewValueWhenSourceChanges() = runTest {
        val x = MutableValueState(2.0)
        val y = MutableValueState(5.0)
        val bindings = mapOf(
            Symbol("x") to x,
            Symbol("y") to y
        )

        val expr = ValueStateAlgebra.interpret("x * 10.0 + y", bindings)

        val channel = Channel<Double?>(Channel.UNLIMITED)
        val job = launch {
            expr.subscribe().collect {
                channel.send(it)
            }
        }

        // Initial emissions from merged source states
        val initialValue = channel.receive()
        assertEquals(25.0, initialValue!!, 1e-9)

        // If both sources emitted initial values during merge, drain any duplicate initial values
        while (!channel.isEmpty) {
            val drain = channel.receive()
            assertEquals(25.0, drain!!, 1e-9)
        }

        // Change source x
        x.value = 3.0
        assertEquals(35.0, channel.receive()!!, 1e-9)

        // Change source y
        y.value = 8.0
        assertEquals(38.0, channel.receive()!!, 1e-9)

        // Change source x again via emit
        x.emit(1.0)
        assertEquals(18.0, channel.receive()!!, 1e-9)

        job.cancel()
    }

    @Test
    fun testSubscriptionWithTime() = runTest {
        val x = MutableValueState(1.0)
        val bindings = mapOf(Symbol("x") to x)

        val expr = ValueStateAlgebra.interpret("sin(x)", bindings)

        val channel = Channel<ValueWithTime<Double?>>(Channel.UNLIMITED)
        val job = launch {
            expr.subscribeWithTime().collect {
                channel.send(it)
            }
        }

        val first = channel.receive()
        assertEquals(sin(1.0), first.value!!, 1e-9)

        // Advance slightly and update
        delay(10.milliseconds)
        x.value = PI / 2
        val second = channel.receive()
        assertEquals(1.0, second.value!!, 1e-9)
        assertTrue(second.time >= first.time)

        job.cancel()
    }
}
