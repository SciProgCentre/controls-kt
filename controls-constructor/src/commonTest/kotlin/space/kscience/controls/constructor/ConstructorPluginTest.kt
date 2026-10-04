package space.kscience.controls.constructor

import kotlinx.coroutines.test.runTest
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ConstructorPluginTest {



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
                        properties = mapOf("value" to ValueStateConfiguration("constant", Meta.EMPTY)),
                    ),
                )
            }
            assertEquals("Value state factory type constant is ambiguous: [a.constant, b.constant]", error.message)

            val tree = constructor.construct(
                ConstructorDeviceConfiguration(
                    properties = mapOf(
                        "first" to ValueStateConfiguration("a.constant", Meta.EMPTY),
                        "second" to ValueStateConfiguration("b.constant", Meta.EMPTY),
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
