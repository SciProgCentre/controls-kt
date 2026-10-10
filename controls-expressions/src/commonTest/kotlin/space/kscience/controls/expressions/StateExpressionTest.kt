package space.kscience.controls.constructor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.*
import space.kscience.controls.expressions.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.nullable
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.math.E
import kotlin.math.PI
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StateExpressionTest {

    @Test
    fun testBasicExpressions() = runTest(timeout = 5.seconds) {
        val context = Context("basicExpressions") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val stateExpressionContext = StateExpressionContext(context,  backgroundScope)

            val a = ValueStateExpression.Constant("pi", Meta.EMPTY)
            val state = stateExpressionContext.computeState(a)
            assertEquals(PI, state.value)

            val b = ValueStateExpression.Binary("+", a, a)
            val state2 = stateExpressionContext.computeState(b)
            assertEquals(PI * 2, state2.value)
        } finally {
            context.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testExpressionStopsWithOwningDevice() = runTest(timeout = 5.seconds) {
        val context = Context("expression-owner-cancellation") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        val t0 = Instant.fromEpochSeconds(1_000)
        val samples = MutableStateFlow(ValueWithTime<Double?>(10.0, t0))
        val source = object : ValueState<Double?> {
            override val valueWithTime get() = samples.value
            override fun subscribeWithTime() = samples
            override fun toString(): String = "TimedSource"
        }
        val device = object : DeviceConstructor(context) {
            val derived by expression(
                ValueStateExpression.Unary("diff", ValueStateExpression.Symbol("x")),
                resolveBinding = { source }
            )
        }
        try {
            device.start()
            val derived = device.derived
            runCurrent()
            assertEquals(1, samples.subscriptionCount.value)

            samples.value = ValueWithTime(20.0, t0 + 1.seconds)
            runCurrent()
            val last = ValueWithTime(10.0, t0 + 1.seconds)
            assertEquals(last, derived.valueWithTime)

            device.stop()
            runCurrent()
            assertEquals(LifecycleState.STOPPED, device.lifecycleState)
            assertTrue(context.isActive)
            assertEquals(0, samples.subscriptionCount.value)

            samples.value = ValueWithTime(40.0, t0 + 2.seconds)
            runCurrent()
            assertEquals(last, derived.valueWithTime)
        } finally {
            device.stop()
            context.cancel()
            context.close()
        }
    }

    class TestDevice(context: Context) : DeviceConstructor(context) {
        val x by virtualProperty(MetaConverter.double, 1.0)
        val y by virtualProperty(MetaConverter.double, 2.0)

        val zState by expression(
            ValueStateExpression.Binary(
                operation = "+",
                left = ValueStateExpression.deviceProperty(deviceName ="test", propertyName = "x"),
                right = ValueStateExpression.deviceProperty(deviceName = "test", propertyName = "y")
            )
        )
    }

    @Test
    fun testDeviceConstructorWithExpression() = runTest(timeout = 5.seconds) {
        val context = Context("deviceExpression") {
            plugin(ConstructorPlugin)
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
            ValueStateExpression.Binary(
                operation = "/",
                left = ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "six"),
                right = ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "three")
            )
        )

        val divisionWithNullState by expression(
            ValueStateExpression.Binary(
                operation = "/",
                left = ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "six"),
                right = ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "nullValue")
            )
        )

        val meanState by expression(
            ValueStateExpression.Function(
                operation = "mean",
                arguments = mapOf(
                    "a" to ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "one"),
                    "b" to ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "two"),
                    "c" to ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "six")
                )
            )
        )

        val meanWithNullState by expression(
            ValueStateExpression.Function(
                operation = "mean",
                arguments = mapOf(
                    "a" to ValueStateExpression.deviceProperty(deviceName ="arithmetic", propertyName = "one"),
                    "b" to ValueStateExpression.deviceProperty(
                        deviceName = "arithmetic",
                        propertyName = "nullValue"
                    ),
                    "c" to ValueStateExpression.deviceProperty(deviceName ="arithmetic", propertyName = "three")
                )
            )
        )

        val meanAllNullState by expression(
            ValueStateExpression.Function(
                operation = "mean",
                arguments = mapOf(
                    "a" to ValueStateExpression.deviceProperty(
                        deviceName = "arithmetic",
                        propertyName = "nullValue"
                    ),
                    "b" to ValueStateExpression.deviceProperty(deviceName = "arithmetic", propertyName = "nullValue")
                )
            )
        )
    }

    @Test
    fun testDivisionExpression() = runTest(timeout = 5.seconds) {
        val context = Context("divisionExpression") {
            plugin(ConstructorPlugin)
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
            plugin(ConstructorPlugin)
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
            val stateExpressionContext = StateExpressionContext(context, backgroundScope)

            val gravity = ValueStateExpression.Constant("gravity", Meta { "value" put 9.81 })
            assertEquals(9.81, stateExpressionContext.computeState(gravity).value)

            assertFailsWith<IllegalStateException> {
                stateExpressionContext.computeState(ValueStateExpression.Constant("unknown", Meta.EMPTY))
            }
        } finally {
            context.close()
        }
    }


    private fun constantProperty(name: String): ValueStateConfiguration = ValueStateConfiguration.expression(
        ValueStateExpression.Constant(name, Meta.EMPTY)
    )


    @Test
    fun testConstructWithFullAndShortValueFactoryNames() = runTest(timeout = 5.seconds) {
        val context = Context("value-factory-names") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val parameters = Meta {
                set(ExpressionValueStateFactory.expression, ValueStateExpression.Constant("pi", Meta.EMPTY))
            }
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "short" to ValueStateConfiguration("expression", parameters),
                        "full" to ValueStateConfiguration("controls-expression.expression", parameters),
                    ),
                ),
            )

            assertEquals(PI, tree.getCachedProperty("short")?.double)
            assertEquals(PI, tree.getCachedProperty("full")?.double)
            assertSame(ExpressionValueStateFactory, constructor.resolveValueStateFactory("expression"))
            assertSame(
                ExpressionValueStateFactory,
                constructor.resolveValueStateFactory("controls-expression.expression")
            )
            assertEquals(setOf("expression", "deviceProperty", "math"), constructor.valueStateFactories.keys)
            assertNull(constructor.resolveValueStateFactory("missing"))
        } finally {
            context.close()
        }
    }

    @Test
    fun testBuildValueStateWithFullFactoryName() = runTest(timeout = 5.seconds) {
        val context = Context("build-value-factory-name") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val state = constructor.buildValueState(Meta {
                "type" put "expression"
                set(ExpressionValueStateFactory.expression, ValueStateExpression.Constant("pi", Meta.EMPTY))
            })

            assertEquals(PI, state.value.double)
            val error = assertFailsWith<IllegalStateException> {
                constructor.buildValueState(Meta { "type" put "missing" })
            }
            assertContains(error.message.orEmpty(), "controls-expression.expression")
            assertContains(error.message.orEmpty(), "controls.constructor.deviceProperty")
        } finally {
            context.close()
        }
    }

    @Test
    fun testStateExpressionWithFullFactoryName() = runTest(timeout = 5.seconds) {
        val context = Context("state-expression-factory-name") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val source = DeviceConstructor(context).apply {
                registerProperty(name = "value", converter = MetaConverter.double, state = ValueState(PI))
            }
            constructor.deviceManager.registerDevice("source", source)
            val expression = ValueStateExpression.State(
                valueStateType = "deviceProperty",
                parameters = Meta {
                    set(DeviceValueStateFactory.deviceName, "source")
                    set(DeviceValueStateFactory.propertyName, "value")
                },
            )
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "value" to ValueStateConfiguration.expression(expression)
                    )
                )
            )

            assertEquals(PI, tree.getCachedProperty("value")?.double)
        } finally {
            context.close()
        }
    }

    private class InputsPlugin : AbstractPlugin() {
        override val tag: PluginTag get() = Companion.tag

        override fun content(target: String): Map<Name, Any> = when (target) {
            DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(Name.of("twoInputs") to TwoInputs)
            else -> super.content(target)
        }

        companion object : PluginFactory<InputsPlugin> {
            override val tag: PluginTag = PluginTag("test.inputs")

            override fun build(context: Context, meta: Meta): InputsPlugin = InputsPlugin()
        }
    }


    private class TwoInputs(context: Context) : DeviceConstructor(context), BoundStateHolder {
        val a = LateBindValueState<Meta>(Meta.EMPTY)
        val b = LateBindValueState<Meta>(Meta.EMPTY)

        override fun bind(state: ValueState<Meta>, inputName: String) = when (inputName) {
            "a" -> a.bind(state)
            "b" -> b.bind(state)
            else -> error("Unknown input $inputName")
        }

        companion object : DeviceFactory {
            override fun buildDevice(context: Context, meta: Meta): Device = TwoInputs(context)
        }
    }


    @Test
    fun testNamedBindingInputs() = runTest(timeout = 5.seconds) {
        val context = Context("named-binding-inputs") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
            plugin(InputsPlugin)
        }
        try {
            val configuration = ConstructorDeviceConfiguration(
                properties = mapOf("pi" to constantProperty("pi"), "e" to constantProperty("e")),
                components = mapOf("target" to TemplateDeviceConfiguration("twoInputs", Meta.EMPTY)),
                bindings = setOf(
                    ConstructorBinding(Name.EMPTY, "pi", Name.of("target"), targetInput = "a"),
                    ConstructorBinding(Name.EMPTY, "e", Name.of("target"), targetInput = "b"),
                ),
            )

            val tree = context.request(ConstructorPlugin).construct(configuration)
            val target = assertIs<TwoInputs>(tree.resolveDevice(Name.of("target")))

            assertEquals(PI, target.a.value.double)
            assertEquals(E, target.b.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testBindingFromNestedDevice() = runTest(timeout = 5.seconds) {
        val context = Context("nested-device-binding") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
            plugin(InputsPlugin)
        }
        try {
            val sensor = ConstructorDeviceConfiguration(properties = mapOf("value" to constantProperty("pi")))
            val configuration = ConstructorDeviceConfiguration(
                properties = emptyMap(),
                devices = mapOf(
                    "group" to ConstructorDeviceConfiguration(
                        properties = emptyMap(),
                        devices = mapOf("sensor" to sensor),
                    ),
                ),
                components = mapOf("target" to TemplateDeviceConfiguration("twoInputs", Meta.EMPTY)),
                bindings = setOf(
                    ConstructorBinding(Name.of("group", "sensor"), "value", Name.of("target"), targetInput = "a"),
                ),
            )

            val tree = context.request(ConstructorPlugin).construct(configuration)
            val target = assertIs<TwoInputs>(tree.resolveDevice(Name.of("target")))

            assertEquals(PI, target.a.value.double)
        } finally {
            context.close()
        }
    }

}
