package space.kscience.controls.expressions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.nullable
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextBuilder
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.kmath.ast.parseMath
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * LLM generated code: Tests for ComputationDevice covering direct factory methods (ofMath, ofExpression),
 * metadata building, descriptor validation/errors, plugin device factory registration, and integration
 * with ConstructorPlugin.construct for formula and expression variants with reactive bindings.
 */
class ComputationDeviceTest {

    private class CustomTimedState(initial: ValueWithTime<Double?>) : ValueState<Double?> {
        private val flow = MutableStateFlow(initial)

        override val valueWithTime: ValueWithTime<Double?> get() = flow.value
        override fun subscribeWithTime() = flow
        override fun toString(): String = "CustomTimedState($valueWithTime)"

        suspend fun emit(value: Double?, time: Instant) {
            flow.emit(ValueWithTime(value, time))
        }
    }

    private fun ValueState<Double?>.asMeta(): ValueState<Meta> = map(MetaConverter.double.nullable()::convert)

    private suspend fun TestScope.withTestContext(
        name: String,
        configure: ContextBuilder.() -> Unit = {},
        block: suspend (Context) -> Unit,
    ) {
        val context = Context(name) {
            coroutineContext(backgroundScope.coroutineContext)
            configure()
        }
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    @Test
    fun testDirectOfMathAndOfExpression() = runTest(timeout = 5.seconds) {
        withTestContext("directComputation", {
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }) { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val sourceA = CustomTimedState(ValueWithTime(3.0, t0))
            val sourceB = CustomTimedState(ValueWithTime(4.0, t0))

            // Test ofMath: hypotenuse sqrt(a * a + b * b)
            val mathDevice = ComputationDevice.ofMath(
                context = context,
                mst = "sqrt(a * a + b * b)".parseMath(),
                argNames = listOf("a", "b")
            )
            assertNull(mathDevice.result.value)
            assertNull(mathDevice.readProperty("result").double)

            mathDevice.bind(sourceA.asMeta(), "a")
            assertNull(mathDevice.result.value) // still null until b is bound
            mathDevice.bind(sourceB.asMeta(), "b")
            assertEquals(5.0, mathDevice.result.value)
            assertEquals(5.0, mathDevice.readProperty("result").double)

            // Test reactive update
            sourceA.emit(6.0, t0 + 1.seconds)
            sourceB.emit(8.0, t0 + 1.seconds)
            assertEquals(10.0, mathDevice.result.value)
            assertEquals(10.0, mathDevice.readProperty("result").double)

            // Test ofExpression: x + y
            val expressionDevice = ComputationDevice.ofExpression(
                context = context,
                expression = ValueStateExpression.Binary(
                    operation = "+",
                    left = ValueStateExpression.Symbol("x"),
                    right = ValueStateExpression.Symbol("y")
                ),
                argNames = listOf("x", "y")
            )
            assertNull(expressionDevice.result.value)
            expressionDevice.bind(sourceA.asMeta(), "x")
            expressionDevice.bind(sourceB.asMeta(), "y")
            assertEquals(14.0, expressionDevice.result.value)
            assertEquals(14.0, expressionDevice.readProperty("result").double)

            // Unknown input rejection
            assertFailsWith<IllegalStateException> {
                expressionDevice.bind(sourceA.asMeta(), "unknown")
            }
        }
    }

    @Test
    fun testBuildDeviceFromMetaAndValidation() = runTest(timeout = 5.seconds) {
        withTestContext("buildDeviceMeta", {
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }) { context ->
            // Formula variant
            val formulaMeta = Meta {
                set(ComputationDevice.argNames, listOf("a", "b"))
                set(ComputationDevice.formula, "a * 2 + b")
            }
            val formulaDevice = ComputationDevice.buildDevice(context, formulaMeta)
            assertEquals(setOf("a", "b"), formulaDevice.argNames)

            // Expression variant
            val expressionMeta = Meta {
                set(ComputationDevice.argNames, listOf("x"))
                set(
                    ComputationDevice.expression,
                    ValueStateExpression.Binary(
                        operation = "*",
                        left = ValueStateExpression.Symbol("x"),
                        right = ValueStateExpression.Constant("2", Meta { "value" put 2.0 })
                    )
                )
            }
            val expressionDevice = ComputationDevice.buildDevice(context, expressionMeta)
            assertEquals(setOf("x"), expressionDevice.argNames)

            // Missing argNames error
            assertFailsWith<IllegalStateException> {
                ComputationDevice.buildDevice(context, Meta { set(ComputationDevice.formula, "a + b") })
            }

            // Missing formula/expression error
            assertFailsWith<IllegalStateException> {
                ComputationDevice.buildDevice(context, Meta { set(ComputationDevice.argNames, listOf("a")) })
            }
        }
    }

    @Test
    fun testPluginDeviceFactoryRegistration() = runTest(timeout = 5.seconds) {
        withTestContext("pluginRegistration", {
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }) { context ->
            val plugin = context.request(ControlsExpressionPlugin)
            val factories = plugin.content(DeviceManager.DEVICE_FACTORY_TARGET)
            assertEquals(ComputationDevice, factories[Name.of(ComputationDevice.TYPE)])

            val deviceManager = context.request(DeviceManager)
            assertSame(ComputationDevice, deviceManager.resolveDeviceFactory(ComputationDevice.TYPE))
            assertSame(
                ComputationDevice,
                deviceManager.resolveDeviceFactory("controls-expression.${ComputationDevice.TYPE}")
            )
        }
    }

    @Test
    fun testConstructorPluginConstructWithFormula() = runTest(timeout = 5.seconds) {
        withTestContext("constructFormula", {
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }) { context ->
            val configuration = ConstructorDeviceConfiguration(
                properties = mapOf(
                    "x" to ValueStateConfiguration.math("3.0"),
                    "y" to ValueStateConfiguration.math("4.0"),
                ),
                components = mapOf(
                    "calculator" to TemplateDeviceConfiguration(
                        type = ComputationDevice.TYPE,
                        parameters = Meta {
                            set(ComputationDevice.argNames, listOf("a", "b"))
                            set(ComputationDevice.formula, "a * a + b * b")
                        }
                    )
                ),
                bindings = setOf(
                    ConstructorBinding(
                        sourceDevice = Name.EMPTY,
                        sourceProperty = "x",
                        targetDevice = Name.of("calculator"),
                        targetInput = "a"
                    ),
                    ConstructorBinding(
                        sourceDevice = Name.EMPTY,
                        sourceProperty = "y",
                        targetDevice = Name.of("calculator"),
                        targetInput = "b"
                    )
                )
            )

            val tree = context.request(ConstructorPlugin).construct(configuration)
            val calcDevice = assertIs<ComputationDevice>(tree.resolveDevice(Name.of("calculator")))

            assertEquals(25.0, calcDevice.result.value)
            assertEquals(25.0, calcDevice.readProperty("result").double)
            assertEquals(25.0, tree.resolvePropertyState(Name.of("calculator"), "result").value.double)
        }
    }

    @Test
    fun testConstructorPluginConstructWithExpression() = runTest(timeout = 5.seconds) {
        withTestContext("constructExpression", {
            plugin(ConstructorPlugin)
            plugin(ControlsExpressionPlugin)
        }) { context ->
            val configuration = ConstructorDeviceConfiguration(
                properties = emptyMap(),
                devices = mapOf(
                    "sensor" to ConstructorDeviceConfiguration(
                        properties = mapOf(
                            "valA" to ValueStateConfiguration.math("20.0"),
                            "valB" to ValueStateConfiguration.math("4.0")
                        )
                    )
                ),
                components = mapOf(
                    "divider" to TemplateDeviceConfiguration(
                        type = "controls-expression.${ComputationDevice.TYPE}",
                        parameters = Meta {
                            set(ComputationDevice.argNames, listOf("num", "den"))
                            set(
                                ComputationDevice.expression,
                                ValueStateExpression.Binary(
                                    operation = "/",
                                    left = ValueStateExpression.Symbol("num"),
                                    right = ValueStateExpression.Symbol("den")
                                )
                            )
                        }
                    )
                ),
                bindings = setOf(
                    ConstructorBinding(
                        sourceDevice = Name.of("sensor"),
                        sourceProperty = "valA",
                        targetDevice = Name.of("divider"),
                        targetInput = "num"
                    ),
                    ConstructorBinding(
                        sourceDevice = Name.of("sensor"),
                        sourceProperty = "valB",
                        targetDevice = Name.of("divider"),
                        targetInput = "den"
                    )
                )
            )

            val tree = context.request(ConstructorPlugin).construct(configuration)
            val dividerDevice = assertIs<ComputationDevice>(tree.resolveDevice(Name.of("divider")))

            assertEquals(5.0, dividerDevice.result.value)
            assertEquals(5.0, dividerDevice.readProperty("result").double)
            assertEquals(5.0, tree.resolvePropertyState(Name.of("divider"), "result").value.double)
        }
    }
}
