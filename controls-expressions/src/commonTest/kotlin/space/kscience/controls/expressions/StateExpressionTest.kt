package space.kscience.controls.constructor

import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.*
import space.kscience.controls.constructor.expressions.ExpressionValueStateFactory
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.constructor.expressions.StateExpressionContext
import space.kscience.controls.constructor.expressions.expression
import space.kscience.controls.expressions.ControlsExpressionPlugin
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.nullable
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


    private fun constantProperty(name: String): PropertyConfiguration = PropertyConfiguration(
        type = "expression",
        parameters = ExpressionValueStateFactory.buildMeta(StateExpression.Constant(name, Meta.EMPTY)),
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
            val parameters = ExpressionValueStateFactory.buildMeta(StateExpression.Constant("pi", Meta.EMPTY))
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "short" to PropertyConfiguration("expression", parameters),
                        "full" to PropertyConfiguration("controls-expression.expression", parameters),
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
                "type" put "controls-expression.expression"
                set(ExpressionValueStateFactory.expression, StateExpression.Constant("pi", Meta.EMPTY))
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
            val expression = StateExpression.State(
                valueStateType = "controls.constructor.deviceProperty",
                parameters = Meta {
                    set(DeviceValueStateFactory.deviceName, "source")
                    set(DeviceValueStateFactory.propertyName, "value")
                },
            )
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "value" to PropertyConfiguration(
                            "expression",
                            ExpressionValueStateFactory.buildMeta(expression)
                        ),
                    ),
                ),
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
