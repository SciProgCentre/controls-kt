package space.kscience.controls.manager

import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class FactoryRefreshTest {
    private class TestFactory : DeviceTreeFactory {
        override val descriptor: MetaDescriptor? = null
        override fun build(context: Context, meta: Meta): DeviceTree = DeviceTree()
    }

    private class Factories(pluginName: String) : AbstractPlugin() {
        override val tag = PluginTag(pluginName)
        val entries = mutableMapOf<Name, DeviceTreeFactory>()
        var reads = 0

        override fun content(target: String): Map<Name, Any> = when (target) {
            DeviceManager.DEVICE_FACTORY_TARGET -> {
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
            plugin(DeviceManager)
            plugin(first)
            plugin(second)
        }
        try {
            val manager = context.request(DeviceManager)
            assertNull(manager.factories["a.late".parseAsName()])
            assertNull(manager.resolveDeviceFactory("late"))

            val factory = TestFactory()
            first.entries[Name.of("late")] = factory
            assertSame(factory, manager.factories["a.late".parseAsName()])
            assertSame(factory, manager.resolveDeviceFactory("a.late"))
            first.reads = 0
            second.reads = 0
            assertSame(factory, manager.resolveDeviceFactory("late"))
            assertEquals(1, first.reads)
            assertEquals(1, second.reads)

            val other = TestFactory()
            second.entries[Name.of("late")] = other
            val error = assertFailsWith<IllegalStateException> { manager.resolveDeviceFactory("late") }
            assertEquals("Device factory type late is ambiguous: [a.late, b.late]", error.message)
            assertSame(factory, manager.resolveDeviceFactory("a.late"))
            assertSame(other, manager.resolveDeviceFactory("b.late"))
        } finally {
            context.close()
        }
    }
}
