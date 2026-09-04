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
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import kotlin.math.E
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private fun constantProperty(name: String): PropertyConfiguration = PropertyConfiguration(
        type = "expression",
        parameters = ExpressionValueStateFactory.buildMeta(StateExpression.Constant(name, Meta.EMPTY)),
    )

    @Test
    fun testNamedBindingInputs() = runTest(timeout = 5.seconds) {
        val context = Context("named-binding-inputs") {
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
}
