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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class DeviceManagerTest {
    private class TestDeviceFactory : DeviceTreeFactory {
        override val descriptor: MetaDescriptor? = null

        override fun build(context: Context, meta: Meta): DeviceTree = DeviceTree()
    }

    private class TestFactoryPlugin(
        pluginName: String,
        private val factory: DeviceTreeFactory,
    ) : AbstractPlugin() {
        override val tag: PluginTag = PluginTag(pluginName)

        override fun content(target: String): Map<Name, Any> = when (target) {
            DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(Name.of("alarm") to factory)
            else -> emptyMap()
        }
    }

    private fun withFactories(vararg factories: TestFactoryPlugin, block: (DeviceManager) -> Unit) {
        val context = Context {
            plugin(DeviceManager)
            factories.forEach { plugin(it) }
        }
        try {
            block(context.request(DeviceManager))
        } finally {
            context.close()
        }
    }

    @Test
    fun testResolveDeviceFactoryRejectsAmbiguousShortName() {
        withFactories(
            TestFactoryPlugin("b", TestDeviceFactory()),
            TestFactoryPlugin("a", TestDeviceFactory()),
        ) { manager ->
            val error = assertFailsWith<IllegalStateException> {
                manager.resolveDeviceFactory("alarm")
            }
            assertEquals("Device factory type alarm is ambiguous: [a.alarm, b.alarm]", error.message)
        }
    }

    @Test
    fun testResolveDeviceFactoryByFullName() {
        val firstFactory = TestDeviceFactory()
        val secondFactory = TestDeviceFactory()
        withFactories(
            TestFactoryPlugin("a", firstFactory),
            TestFactoryPlugin("b", secondFactory),
        ) { manager ->
            assertSame(firstFactory, manager.resolveDeviceFactory("a.alarm"))
            assertSame(secondFactory, manager.resolveDeviceFactory("b.alarm"))
        }
    }

    @Test
    fun testResolveDeviceFactoryByUniqueShortName() {
        val factory = TestDeviceFactory()
        withFactories(TestFactoryPlugin("a", factory)) { manager ->
            assertSame(factory, manager.resolveDeviceFactory("alarm"))
        }
    }

    @Test
    fun testResolveDeviceFactoryReturnsNullForUnknownType() {
        withFactories(TestFactoryPlugin("a", TestDeviceFactory())) { manager ->
            assertNull(manager.resolveDeviceFactory("missing"))
            assertNull(manager.resolveDeviceFactory("a.missing"))
        }
    }

    @Test
    fun testCreateDeviceTreeRejectsMissingType() {
        withFactories { manager ->
            val error = assertFailsWith<IllegalStateException> {
                manager.createDeviceTree(Meta.EMPTY)
            }
            assertTrue(error.message.orEmpty().contains("RequiredValueIsMissing"))
        }
    }

    @Test
    fun testCreateDeviceTreeRejectsIncorrectTypeValue() {
        withFactories { manager ->
            val error = assertFailsWith<IllegalStateException> {
                manager.createDeviceTree(Meta { "type" put 42 })
            }
            assertTrue(error.message.orEmpty().contains("IncorrectValueType"))
        }
    }
}
