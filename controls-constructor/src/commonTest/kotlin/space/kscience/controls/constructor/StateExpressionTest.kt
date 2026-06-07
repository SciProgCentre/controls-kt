package space.kscience.controls.constructor

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.awaitLifecycleState
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.constructor.expressions.StateExpressionContext
import space.kscience.controls.constructor.expressions.expression
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.installTree
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.asName
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StateExpressionTest {

    @Test
    fun testBasicExpressions() = runTest(timeout = 20.seconds) {
        val context = Context("test")
        val stateExpressionContext = StateExpressionContext(DeviceTree(), backgroundScope)

        val a = StateExpression.Constant("pi", Meta.EMPTY)
        val state = stateExpressionContext.computeState(a)
        assertEquals(PI, state.value)

        val b = StateExpression.Binary("+", a, a)
        val state2 = stateExpressionContext.computeState(b)
        assertEquals(PI * 2, state2.value)
    }

    class TestDevice(context: Context) : DeviceConstructor(context) {
        val x by virtualProperty(MetaConverter.double, 1.0)
        val y by virtualProperty(MetaConverter.double, 2.0)

        val zState by expression(
            StateExpression.Binary(
                operation = "+",
                left = StateExpression.Property(deviceName = "test".asName(), propertyName = "x"),
                right = StateExpression.Property(deviceName = "test".asName(), propertyName = "y")
            )
        )
    }

    @Test
    fun testDeviceConstructorWithExpression() = runTest(timeout = 500.milliseconds) {
        val context = Context("test") {
            plugin(DeviceManager.Companion)
        }
        val device = TestDevice(context)

        launch {
            device.awaitLifecycleState(LifecycleState.STARTING)

            assertEquals(3.0, device.zState.value)

            context.close()
        }
        delay(10.milliseconds)

        context.installTree("test", device)

    }
}