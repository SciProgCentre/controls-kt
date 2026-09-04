package space.kscience.controls.constructor

import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceFactory
import space.kscience.controls.api.resolveDevice
import space.kscience.controls.constructor.expressions.StateExpression
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.math.E
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class ConstructorPluginTest {

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

    private class ConstantFactory(private val value: Double) : ValueStateFactory {
        override val descriptor: MetaDescriptor? = null

        override fun build(context: Context, meta: Meta): ValueState<Meta> = ValueState(Meta(value))
    }

    private class ValueFactoryPlugin(pluginName: String, value: Double) : AbstractPlugin() {
        override val tag: PluginTag = PluginTag(pluginName)
        private val factory = ConstantFactory(value)

        override fun content(target: String): Map<Name, Any> = when (target) {
            ValueStateFactory.PROVIDER_TAGET -> mapOf(Name.of("constant") to factory)
            else -> super.content(target)
        }
    }

    private fun constantProperty(name: String): PropertyConfiguration = PropertyConfiguration(
        type = "expression",
        parameters = ExpressionValueStateFactory.buildMeta(StateExpression.Constant(name, Meta.EMPTY)),
    )

    @Test
    fun testNamedBindingInputs() = runTest(timeout = 5.seconds) {
        val context = Context("named-binding-inputs") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
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

    @Test
    fun testConstructWithFullAndShortValueFactoryNames() = runTest(timeout = 5.seconds) {
        val context = Context("value-factory-names") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val parameters = ExpressionValueStateFactory.buildMeta(StateExpression.Constant("pi", Meta.EMPTY))
            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "short" to PropertyConfiguration("expression", parameters),
                        "full" to PropertyConfiguration("controls.constructor.expression", parameters),
                    ),
                ),
            )

            assertEquals(PI, tree.getCachedProperty("short")?.double)
            assertEquals(PI, tree.getCachedProperty("full")?.double)
            assertSame(ExpressionValueStateFactory, constructor.resolveValueStateFactory("expression"))
            assertSame(ExpressionValueStateFactory, constructor.resolveValueStateFactory("controls.constructor.expression"))
            assertEquals(setOf("expression", "deviceProperty"), constructor.valueStateFactories.keys)
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
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val state = constructor.buildValueState(Meta {
                "type" put "controls.constructor.expression"
                set(ExpressionValueStateFactory.expression, StateExpression.Constant("pi", Meta.EMPTY))
            })

            assertEquals(PI, state.value.double)
            val error = assertFailsWith<IllegalStateException> {
                constructor.buildValueState(Meta { "type" put "missing" })
            }
            assertContains(error.message.orEmpty(), "controls.constructor.expression")
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
                        "value" to PropertyConfiguration("expression", ExpressionValueStateFactory.buildMeta(expression)),
                    ),
                ),
            )

            assertEquals(PI, tree.getCachedProperty("value")?.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testConstructRejectsAmbiguousShortFactoryName() = runTest(timeout = 5.seconds) {
        val context = Context("ambiguous-value-factories") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ConstructorPlugin)
            plugin(ValueFactoryPlugin("b", 2.0))
            plugin(ValueFactoryPlugin("a", 1.0))
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            val error = assertFailsWith<IllegalStateException> {
                constructor.construct(
                    ConstructorDeviceConfiguration(
                        properties = mapOf("value" to PropertyConfiguration("constant", Meta.EMPTY)),
                    ),
                )
            }
            assertEquals("Value state factory type constant is ambiguous: [a.constant, b.constant]", error.message)

            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "first" to PropertyConfiguration("a.constant", Meta.EMPTY),
                        "second" to PropertyConfiguration("b.constant", Meta.EMPTY),
                    ),
                ),
            )
            assertEquals(1.0, tree.getCachedProperty("first")?.double)
            assertEquals(2.0, tree.getCachedProperty("second")?.double)
            assertContains(constructor.valueStateFactories.keys, "constant")
        } finally {
            context.close()
        }
    }
}
