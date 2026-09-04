package space.kscience.controls.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Clock

internal class DeviceTreeTest {
    private val device: Device = object : Device {
        override val propertyDescriptors: Collection<PropertyDescriptor> = emptyList()
        override val actionDescriptors: Collection<ActionDescriptor> = emptyList()
        override val context: Context get() = error("Not implemented")
        override val lifecycleState: LifecycleState get() = LifecycleState.STARTED
        override val messageFlow: Flow<DeviceMessage> get() = emptyFlow()
        override val clock: Clock get() = Clock.System
        override suspend fun readProperty(propertyName: String): Meta = Meta.EMPTY
        override suspend fun writeProperty(propertyName: String, value: Meta) {}
        override suspend fun execute(actionName: String, argument: Meta?): Meta? = null
        override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
    }

    private val tree = DeviceTree(children = mapOf("a" to DeviceTree(device)))

    @Test
    fun testResolveDeviceOrNullReturnsNullForMissingNestedDevice() {
        assertNull(tree.resolveDeviceOrNull(Name.of("a", "missing")))
    }

    @Test
    fun testResolveDeviceOrNullReturnsNullForMissingAncestor() {
        assertNull(tree.resolveDeviceOrNull(Name.of("missing", "x")))
    }

    @Test
    fun testResolveDeviceOrNullReturnsExistingDevice() {
        assertSame(device, tree.resolveDeviceOrNull(Name.of("a")))
    }

    @Test
    fun testResolveDeviceThrowsForMissingNestedDevice() {
        assertFailsWith<IllegalStateException> {
            tree.resolveDevice(Name.of("a", "missing"))
        }
    }
}
