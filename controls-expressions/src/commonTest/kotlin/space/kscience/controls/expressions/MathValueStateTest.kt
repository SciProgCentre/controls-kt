package space.kscience.controls.expressions

import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
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

/*
 * LLM generated code: Tests for formula-based math expressions (MathValueStateFactory),
 * verifying short and full factory name resolution, metadata building, device property dependencies,
 * reactive property updates, nested dependencies, cross-compatibility with syntax-tree expressions,
 * and named device input bindings.
 */
class MathValueStateTest {

    @Test
    fun testConstructWithFullAndShortValueFactoryNames() = runTest(timeout = 5.seconds) {
        val context = Context("math-factory-names") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val parameters = Meta { set(MathValueStateFactory.expression, "2.0 * 3.0") }
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "short" to ValueStateConfiguration("math", parameters),
                        "full" to ValueStateConfiguration("controls-expression.math", parameters),
                    ),
                ),
            )

            assertEquals(6.0, tree.getCachedProperty("short")?.double)
            assertEquals(6.0, tree.getCachedProperty("full")?.double)
            assertSame(MathValueStateFactory, constructor.resolveValueStateFactory("math"))
            assertSame(
                MathValueStateFactory,
                constructor.resolveValueStateFactory("controls-expression.math")
            )
            assertEquals(setOf("deviceProperty", "expression", "math"), constructor.valueStateFactories.keys)
            assertNull(constructor.resolveValueStateFactory("missing"))
        } finally {
            context.close()
        }
    }

    @Test
    fun testBuildValueStateWithFullFactoryName() = runTest(timeout = 5.seconds) {
        val context = Context("build-math-factory-name") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val state = constructor.buildValueState(Meta {
                "type" put "math"
                set(MathValueStateFactory.expression, "10.0 + 5.0")
            })
            assertEquals(15.0, state.value.double)

            val fullState = constructor.buildValueState(Meta {
                "type" put "controls-expression.math"
                set(MathValueStateFactory.expression, "20.0 / 4.0")
            })
            assertEquals(5.0, fullState.value.double)

            val error = assertFailsWith<IllegalStateException> {
                constructor.buildValueState(Meta { "type" put "missing" })
            }
            assertContains(error.message.orEmpty(), "controls-expression.math")
            assertContains(error.message.orEmpty(), "controls.constructor.deviceProperty")
        } finally {
            context.close()
        }
    }

    @Test
    fun testMathExpressionWithDevicePropertyDependency() = runTest(timeout = 5.seconds) {
        val context = Context("math-device-property") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val source = DeviceConstructor(context).apply {
                registerProperty(name = "x", converter = MetaConverter.double, state = ValueState(3.0))
                registerProperty(name = "y", converter = MetaConverter.double, state = ValueState(4.0))
            }
            constructor.deviceManager.registerDevice("source", source)

            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "hypot" to ValueStateConfiguration.math(
                            expression = "sqrt(x * x + y * y)",
                            arguments = mapOf(
                                "x" to ValueStateConfiguration.deviceProperty("source", "x"),
                                "y" to ValueStateConfiguration.deviceProperty("source", "y")
                            ),
                        )
                    )
                )
            )

            assertEquals(5.0, tree.getCachedProperty("hypot")?.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testMathExpressionReactiveUpdates() = runTest(timeout = 5.seconds) {
        val context = Context("math-reactive-updates") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val mutableState = MutableValueState(2.0)
            val source = DeviceConstructor(context).apply {
                registerProperty(name = "x", converter = MetaConverter.double, state = mutableState)
            }
            constructor.deviceManager.registerDevice("source", source)

            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "scaled" to ValueStateConfiguration.math(
                            expression = "x * 10.0 + 1.0",
                            arguments = mapOf(
                                "x" to ValueStateConfiguration.deviceProperty("source", "x")
                            )
                        )
                    )
                )
            )

            val propertyState = tree.resolvePropertyState(Name.EMPTY, "scaled")
            assertEquals(21.0, propertyState.value.double)

            mutableState.value = 5.0
            assertEquals(51.0, propertyState.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testMathExpressionWithNestedMathDependencies() = runTest(timeout = 5.seconds) {
        val context = Context("math-nested-dependencies") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "composite" to ValueStateConfiguration.math(
                            expression = "a + b * 2.0",
                            arguments = mapOf(
                                "a" to ValueStateConfiguration.math(expression = "10.0 + 5.0"),
                                "b" to ValueStateConfiguration.math("3.0 * 4.0")
                            ),
                        )
                    )
                )
            )

            // a = 15.0, b = 12.0; 15.0 + 12.0 * 2.0 = 39.0
            assertEquals(
                39.0, tree.getCachedProperty("composite")?.double
            )
        } finally {
            context.close()
        }
    }

    @Test
    fun testMathExpressionWithStateExpressionDependency() = runTest(timeout = 5.seconds) {
        val context = Context("math-with-state-expression") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "result" to ValueStateConfiguration.math(
                            expression = "2.0 * p",
                            arguments = mapOf(
                                "p" to ValueStateConfiguration.expression(
                                    ValueStateExpression.Constant(
                                        "pi",
                                        Meta.EMPTY
                                    )
                                )
                            )
                        )
                    )
                )
            )

            assertEquals(2.0 * PI, tree.getCachedProperty("result")?.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testStateExpressionWithMathStateDependency() = runTest(timeout = 5.seconds) {
        val context = Context("state-expression-with-math") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val mathValueStateExpression = ValueStateExpression.State(
                valueStateType = "math",
                parameters = Meta { set(MathValueStateFactory.expression, "5.0 * 6.0") },
            )
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "result" to ValueStateConfiguration.expression(
                            ValueStateExpression.Binary(
                                operation = "+",
                                left = mathValueStateExpression,
                                right = ValueStateExpression.Constant("e", Meta.EMPTY)
                            )
                        )
                    )
                )
            )

            assertEquals(30.0 + E, tree.getCachedProperty("result")?.double)
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
            override val tag: PluginTag = PluginTag("test.math.inputs")

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
        val context = Context("named-binding-math-inputs") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
            plugin(InputsPlugin)
        }
        try {
            val configuration = ConstructorDeviceConfiguration(
                properties = mapOf(
                    "pi" to ValueStateConfiguration.math("3.141592653589793"),
                    "e" to ValueStateConfiguration.math("2.718281828459045")
                ),
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
        val context = Context("nested-device-math-binding") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
            plugin(InputsPlugin)
        }
        try {
            val sensor = ConstructorDeviceConfiguration(
                properties = mapOf("value" to ValueStateConfiguration.math("4.0 * 2.5"))
            )
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

            assertEquals(10.0, target.a.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testMathExpressionEvaluationFormulas() = runTest(timeout = 5.seconds) {
        val context = Context("math-formula-evaluation") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)

            // Trigonometric and algebraic operations
            val trigState = constructor.buildValueState(Meta {
                "type" put "math"
                set(MathValueStateFactory.expression, "sin(0.0) + cos(0.0) * 5.0 + (atan(1.0) * 4.0 / pi)")
                set(
                    MathValueStateFactory.arguments,
                    mapOf(
                        "pi" to ValueStateConfiguration.expression(
                            ValueStateExpression.Constant("pi", Meta.EMPTY)
                        )
                    )
                )
            })
            // 0.0 + 5.0 + (PI / PI) = 6.0
            assertEquals(6.0, trigState.value.double)

            // Division and nested brackets
            val divState = constructor.buildValueState(Meta {
                "type" put "math"
                set(MathValueStateFactory.expression, "((10.0 + 20.0) / 6.0) * (8.0 - 3.0)")
            })
            assertEquals(25.0, divState.value.double)
        } finally {
            context.close()
        }
    }
}
