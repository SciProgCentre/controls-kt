package space.kscience.controls.constructor

import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class FactoryRefreshTest {
    private class TestFactory(private val value: Int) : ValueStateFactory {
        override val descriptor: MetaDescriptor? = null
        override fun build(context: Context, meta: Meta): ValueState<Meta> = ValueState(Meta(value))
    }

    private class Factories(pluginName: String) : AbstractPlugin() {
        override val tag = PluginTag(pluginName)
        val entries = mutableMapOf<Name, ValueStateFactory>()
        var reads = 0

        override fun content(target: String): Map<Name, Any> = when (target) {
            ValueStateFactory.PROVIDER_TAGET -> {
                reads++
                entries
            }
            else -> emptyMap()
        }
    }

    @Test
    fun resolvesFactoriesAddedAfterFirstLookup() {
        val first = Factories("a")
        val second = Factories("b")
        val context = Context {
            plugin(ConstructorPlugin)
            plugin(first)
            plugin(second)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            assertNull(constructor.valueStateFactories["late"])
            assertNull(constructor.resolveValueStateFactory("late"))

            val factory = TestFactory(1)
            first.entries[Name.of("late")] = factory
            assertSame(factory, constructor.valueStateFactories["late"])
            assertSame(factory, constructor.resolveValueStateFactory("a.late"))
            first.reads = 0
            second.reads = 0
            assertSame(factory, constructor.resolveValueStateFactory("late"))
            assertEquals(1, first.reads)
            assertEquals(1, second.reads)
            assertEquals(Meta(1), constructor.buildValueState(Meta.EMPTY, "a.late").value)

            val other = TestFactory(2)
            second.entries[Name.of("late")] = other
            val error = assertFailsWith<IllegalStateException> { constructor.resolveValueStateFactory("late") }
            assertEquals("Value state factory type late is ambiguous: [a.late, b.late]", error.message)
            assertSame(factory, constructor.resolveValueStateFactory("a.late"))
            assertSame(other, constructor.resolveValueStateFactory("b.late"))
        } finally {
            context.close()
        }
    }
}
