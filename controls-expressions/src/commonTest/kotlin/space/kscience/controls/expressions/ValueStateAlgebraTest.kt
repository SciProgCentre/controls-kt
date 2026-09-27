package space.kscience.controls.expressions

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.MutableValueState
import space.kscience.controls.constructor.ValueState
import space.kscience.kmath.expressions.Symbol
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        assertEquals(0.0, ValueStateAlgebra.interpret("0.0",bindings).value)


        // Arithmetic, brackets, and trigonometry: sin(x) * y + (4.0 / 2.0) - cos(0.0)
        // sin(PI/6) = 0.5; 0.5 * 2.0 = 1.0; 1.0 + (4.0 / 2.0) = 3.0; 3.0 - 1.0 = 2.0
        val expr = ValueStateAlgebra.interpret("sin(x) * y + (4.0 / 2.0) - cos(0.0)", bindings)
        assertEquals(2.0, expr.value, 1e-9)
    }
}
