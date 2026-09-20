package space.kscience.controls.constructor

import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.awaitLifecycleState
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.constructor.expressions.StateExpressionContext
import space.kscience.controls.constructor.expressions.expression
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class StateExpressionTest {

    @Test
    fun testBasicExpressions() = runTest(timeout = 5.seconds) {
        val context = Context("basicExpressions") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val stateExpressionContext = StateExpressionContext(context, DeviceTree(), backgroundScope)

            val a = StateExpression.Constant("pi", Meta.EMPTY)
            val state = stateExpressionContext.computeState(a)
            assertEquals(PI, state.value)

            val b = StateExpression.Binary("+", a, a)
            val state2 = stateExpressionContext.computeState(b)
            assertEquals(PI * 2, state2.value)
        } finally {
            context.close()
        }
    }

    class TestDevice(context: Context) : DeviceConstructor(context) {
        val x by virtualProperty(MetaConverter.double, 1.0)
        val y by virtualProperty(MetaConverter.double, 2.0)

        val zState by expression(
            StateExpression.Binary(
                operation = "+",
                left = StateExpression.Property(deviceName = Name.of("test"), propertyName = "x"),
                right = StateExpression.Property(deviceName = Name.of("test"), propertyName = "y")
            )
        )
    }

    @Test
    fun testDeviceConstructorWithExpression() = runTest(timeout = 5.seconds) {
        val context = Context("deviceExpression") {
            plugin(DeviceManager)
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = context.install("test", TestDevice(context))
            device.awaitLifecycleState(LifecycleState.STARTED)
            assertEquals(3.0, device.zState.value)
        } finally {
            context.close()
        }
    }

    class ArithmeticTestDevice(context: Context) : DeviceConstructor(context) {
        val six by virtualProperty(MetaConverter.double, 6.0)
        val three by virtualProperty(MetaConverter.double, 3.0)
        val one by virtualProperty(MetaConverter.double, 1.0)
        val two by virtualProperty(MetaConverter.double, 2.0)
        val nullValue by virtualProperty(MetaConverter.double.nullable(), null)

        val divisionState by expression(
            StateExpression.Binary(
                operation = "/",
                left = StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "six"),
                right = StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "three")
            )
        )

        val divisionWithNullState by expression(
            StateExpression.Binary(
                operation = "/",
                left = StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "six"),
                right = StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "nullValue")
            )
        )

        val meanState by expression(
            StateExpression.Nary(
                operation = "mean",
                arguments = mapOf(
                    "a" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "one"),
                    "b" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "two"),
                    "c" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "six")
                )
            )
        )

        val meanWithNullState by expression(
            StateExpression.Nary(
                operation = "mean",
                arguments = mapOf(
                    "a" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "one"),
                    "b" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "nullValue"),
                    "c" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "three")
                )
            )
        )

        val meanAllNullState by expression(
            StateExpression.Nary(
                operation = "mean",
                arguments = mapOf(
                    "a" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "nullValue"),
                    "b" to StateExpression.Property(deviceName = Name.of("arithmetic"), propertyName = "nullValue")
                )
            )
        )
    }

    @Test
    fun testDivisionExpression() = runTest(timeout = 5.seconds) {
        val context = Context("divisionExpression") {
            plugin(DeviceManager)
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = context.install("arithmetic", ArithmeticTestDevice(context))
            device.awaitLifecycleState(LifecycleState.STARTED)
            assertEquals(2.0, device.divisionState.value)
            assertEquals(null, device.divisionWithNullState.value)
        } finally {
            context.close()
        }
    }

    @Test
    fun testMeanExpression() = runTest(timeout = 5.seconds) {
        val context = Context("meanExpression") {
            plugin(DeviceManager)
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = context.install("arithmetic", ArithmeticTestDevice(context))
            device.awaitLifecycleState(LifecycleState.STARTED)
            assertEquals(3.0, device.meanState.value)
            assertEquals(2.0, device.meanWithNullState.value)
            assertEquals(null, device.meanAllNullState.value)
        } finally {
            context.close()
        }
    }

    @Test
    fun testConstantWithParameterValue() = runTest(timeout = 5.seconds) {
        val context = Context("constantWithParameter") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val stateExpressionContext = StateExpressionContext(context, DeviceTree(), backgroundScope)

            val gravity = StateExpression.Constant("gravity", Meta { "value" put 9.81 })
            assertEquals(9.81, stateExpressionContext.computeState(gravity).value)

            assertFailsWith<IllegalStateException> {
                stateExpressionContext.computeState(StateExpression.Constant("unknown", Meta.EMPTY))
            }
        } finally {
            context.close()
        }
    }
}
