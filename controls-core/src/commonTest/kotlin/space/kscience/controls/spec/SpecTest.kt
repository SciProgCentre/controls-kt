package space.kscience.controls.spec

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

internal class SpecTest {

    private fun mockDevice(
        properties: Collection<PropertyDescriptor> = emptyList(),
        actions: Collection<ActionDescriptor> = emptyList()
    ): Device = object : Device {
        override val propertyDescriptors: Collection<PropertyDescriptor> = properties
        override val actionDescriptors: Collection<ActionDescriptor> = actions

        override val context: Context get() = error("Not implemented")
        override val lifecycleState: LifecycleState get() = LifecycleState.STARTED
        override val messageFlow: Flow<DeviceMessage> get() = emptyFlow()
        override val clock: Clock get() = Clock.System
        override suspend fun readProperty(propertyName: String): Meta = Meta.EMPTY
        override suspend fun writeProperty(propertyName: String, value: Meta) {}
        override suspend fun execute(actionName: String, argument: Meta?): Meta? = null
        override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
    }

    @Test
    fun testDeviceSpecCheckMissingElements() {
        val prop1 = PropertyDescriptor("prop1")
        val prop2 = PropertyDescriptor("prop2")
        val action1 = ActionDescriptor("action1")

        val spec = DeviceSpec(
            properties = mapOf(
                "prop1" to DevicePropertySpec(MetaConverter.meta, prop1),
                "prop2" to DevicePropertySpec(MetaConverter.meta, prop2)
            ),
            actions = mapOf(
                "action1" to DeviceActionSpec(MetaConverter.meta, MetaConverter.meta, action1)
            )
        )

        val device = mockDevice(
            properties = listOf(prop1),
            actions = listOf(action1)
        )

        val missing = spec.checkMissingElements(device)
        assertEquals(1, missing.size)
        assertTrue(missing.contains(prop2))
    }

    @Test
    fun testDeviceTreeSpecCheckMissingElements() {
        val prop1 = PropertyDescriptor("prop1")
        val deviceSpec = DeviceSpec(
            properties = mapOf("prop1" to DevicePropertySpec(MetaConverter.meta, prop1))
        )

        val childSpec = DeviceTreeSpec(device = deviceSpec)
        val rootSpec = DeviceTreeSpec(
            children = mapOf("child" to childSpec)
        )

        // Case 1: Everything missing
        val missing1 = rootSpec.checkMissingElements(null)
        assertEquals(1, missing1.size)
        assertEquals(setOf(prop1 as DeviceElementDescriptor), missing1["child".asName()])

        // Case 2: Partial missing
        val device = mockDevice(properties = listOf(prop1))
        val deviceTree = DeviceTree(
            children = mapOf("child" to DeviceTree(rootDevice = device))
        )
        val missing2 = rootSpec.checkMissingElements(deviceTree)
        assertTrue(missing2.isEmpty())

        // Case 3: Root device missing
        val rootSpecWithDevice = DeviceTreeSpec(
            device = deviceSpec,
            children = mapOf("child" to childSpec)
        )
        val missing3 = rootSpecWithDevice.checkMissingElements(deviceTree)
        assertEquals(1, missing3.size)
        assertEquals(setOf(prop1 as DeviceElementDescriptor), missing3[Name.EMPTY])
    }
}
