@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.manager

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

internal class DeviceTreeMessageFlowTest {
    private class TestDevice(messages: Flow<DeviceMessage>? = null) : Device {
        val events = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 1)
        var subscriptionStarts: Int = 0
            private set

        override val messageFlow: Flow<DeviceMessage> = messages ?: events.onSubscription { subscriptionStarts++ }
        override val propertyDescriptors: Collection<PropertyDescriptor> = emptyList()
        override val actionDescriptors: Collection<ActionDescriptor> = emptyList()
        override val context: Context get() = error("Not used by this fixture")
        override val lifecycleState: LifecycleState = LifecycleState.STARTED
        override val clock: Clock = Clock.System
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        override suspend fun readProperty(propertyName: String): Meta = Meta.EMPTY
        override suspend fun writeProperty(propertyName: String, value: Meta) {}
        override suspend fun execute(actionName: String, argument: Meta?): Meta? = null
    }

    private fun message(property: String): PropertyChangedMessage = PropertyChangedMessage(
        time = Instant.fromEpochMilliseconds(0),
        property = property,
        value = Meta.EMPTY,
    )

    @Test
    fun testRegisteringSameTreeDoesNotPublishChanges() = runTest {
        val manager = DeviceManager()
        var snapshots = 0
        backgroundScope.launch { manager.childrenFlow().collect { snapshots++ } }
        runCurrent()
        val child = DeviceTree(TestDevice())
        manager.registerDeviceTree("child", child)
        runCurrent()
        repeat(10) {
            manager.registerDeviceTree("child", child)
            runCurrent()
        }
        assertEquals(2, snapshots)
    }

    @Test
    fun testLateRegistration() = runTest {
        val manager = DeviceManager()
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { manager.messageFlow().collect { received.add(it) } }
        runCurrent()

        val device = TestDevice()
        manager.registerDevice("late", device)
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)

        val event = message("late-update")
        assertTrue(device.events.tryEmit(event))
        runCurrent()

        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("late"))), received)
    }

    @Test
    fun testReplacementSwitchesListener() = runTest {
        val manager = DeviceManager()
        val oldDevice = TestDevice()
        manager.registerDevice("device", oldDevice)
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { manager.messageFlow().collect { received.add(it) } }
        runCurrent()
        assertEquals(1, oldDevice.events.subscriptionCount.value)

        val before = message("before-replacement")
        assertTrue(oldDevice.events.tryEmit(before))
        runCurrent()

        val replacement = TestDevice()
        manager.registerDevice("device", replacement)
        runCurrent()
        assertEquals(0, oldDevice.events.subscriptionCount.value)
        assertEquals(1, replacement.events.subscriptionCount.value)

        assertTrue(oldDevice.events.tryEmit(message("removed-device-update")))
        val after = message("replacement-update")
        assertTrue(replacement.events.tryEmit(after))
        runCurrent()

        assertEquals(
            listOf<DeviceMessage>(
                before.copy(sourceDevice = Name.of("device")),
                after.copy(sourceDevice = Name.of("device")),
            ),
            received,
        )
    }

    @Test
    fun testRootReplacementSwitchesListener() = runTest {
        val original = TestDevice()
        val roots = MutableSharedFlow<Device?>(replay = 1)
        roots.tryEmit(original)
        val tree = object : DeviceTree {
            override val device: Device? get() = roots.replayCache.last()
            override val children: Map<String, DeviceTree> = emptyMap()
            override fun deviceFlow(): Flow<Device?> = roots
        }
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { tree.messageFlow().collect { received.add(it) } }
        runCurrent()
        assertEquals(1, original.events.subscriptionCount.value)

        roots.emit(original)
        runCurrent()
        assertEquals(1, original.subscriptionStarts)

        val replacement = TestDevice()
        roots.emit(replacement)
        runCurrent()
        assertEquals(0, original.events.subscriptionCount.value)
        assertEquals(1, replacement.events.subscriptionCount.value)
        assertTrue(original.events.tryEmit(message("removed-root-update")))
        val event = message("replacement-update")
        assertTrue(replacement.events.tryEmit(event))
        runCurrent()
        assertEquals(listOf<DeviceMessage>(event), received)

        roots.emit(null)
        runCurrent()
        assertEquals(0, replacement.events.subscriptionCount.value)
        assertTrue(replacement.events.tryEmit(message("detached-root-update")))
        runCurrent()
        assertEquals(listOf<DeviceMessage>(event), received)

        roots.emit(replacement)
        runCurrent()
        assertEquals(1, replacement.events.subscriptionCount.value)
        assertEquals(2, replacement.subscriptionStarts)
    }

    @Test
    fun testLateGroupPrefixesMessages() = runTest {
        val manager = DeviceManager()
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { manager.messageFlow().collect { received.add(it) } }
        runCurrent()

        val root = TestDevice()
        val leaf = TestDevice()
        manager.registerDeviceTree(
            "group",
            DeviceTree(root, mapOf("nested" to DeviceTree(children = mapOf("leaf" to DeviceTree(leaf))))),
        )
        runCurrent()
        assertEquals(1, root.events.subscriptionCount.value)
        assertEquals(1, leaf.events.subscriptionCount.value)

        val rootEvent = message("group-update")
        assertTrue(root.events.tryEmit(rootEvent))
        runCurrent()
        val leafEvent = message("leaf-update")
        assertTrue(leaf.events.tryEmit(leafEvent))
        runCurrent()

        assertEquals(
            listOf<DeviceMessage>(
                rootEvent.copy(sourceDevice = Name.of("group")),
                leafEvent.copy(sourceDevice = Name.of("group", "nested", "leaf")),
            ),
            received,
        )
    }

    @Test
    fun testNestedRegistration() = runTest {
        val manager = DeviceManager()
        val nestedManager = DeviceManager()
        manager.registerDeviceTree("group", nestedManager)
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { manager.messageFlow().collect { received.add(it) } }
        runCurrent()

        val device = TestDevice()
        nestedManager.registerDevice("late", device)
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)

        val event = message("nested-update")
        assertTrue(device.events.tryEmit(event))
        runCurrent()

        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("group", "late"))), received)
    }

    @Test
    fun testTreeIsReadAtCollection() = runTest {
        val manager = DeviceManager()
        val messages = manager.messageFlow()
        val device = TestDevice()
        manager.registerDevice("late", device)

        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { messages.collect { received.add(it) } }
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)

        val event = message("current-tree-update")
        assertTrue(device.events.tryEmit(event))
        runCurrent()

        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("late"))), received)
    }

    @Test
    fun testAdditionKeepsSiblingSubscribed() = runTest {
        val manager = DeviceManager()
        val stable = TestDevice()
        manager.registerDevice("stable", stable)
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { manager.messageFlow().collect { received.add(it) } }
        runCurrent()
        assertEquals(1, stable.events.subscriptionCount.value)
        assertEquals(1, stable.subscriptionStarts)

        val added = TestDevice()
        manager.registerDevice("added", added)
        runCurrent()
        assertEquals(1, stable.events.subscriptionCount.value)
        assertEquals(1, stable.subscriptionStarts)
        assertEquals(1, added.events.subscriptionCount.value)

        val stableEvent = message("stable-update")
        assertTrue(stable.events.tryEmit(stableEvent))
        runCurrent()
        val addedEvent = message("added-update")
        assertTrue(added.events.tryEmit(addedEvent))
        runCurrent()

        assertEquals(
            listOf<DeviceMessage>(
                stableEvent.copy(sourceDevice = Name.of("stable")),
                addedEvent.copy(sourceDevice = Name.of("added")),
            ),
            received,
        )
    }

    @Test
    fun testCancellationReleasesListeners() = runTest {
        val manager = DeviceManager()
        val direct = TestDevice()
        val groupRoot = TestDevice()
        val leaf = TestDevice()
        manager.registerDevice("direct", direct)
        manager.registerDeviceTree("group", DeviceTree(groupRoot, mapOf("leaf" to DeviceTree(leaf))))
        val collector = backgroundScope.launch { manager.messageFlow().collect {} }
        runCurrent()
        listOf(direct, groupRoot, leaf).forEach { assertEquals(1, it.events.subscriptionCount.value) }

        collector.cancel()
        runCurrent()

        assertTrue(collector.isCompleted)
        listOf(direct, groupRoot, leaf).forEach { assertEquals(0, it.events.subscriptionCount.value) }
        val addedAfterCancellation = TestDevice()
        manager.registerDevice("after-cancellation", addedAfterCancellation)
        runCurrent()
        assertEquals(0, addedAfterCancellation.events.subscriptionCount.value)
    }

    @Test
    fun testRemovingSubtreeKeepsSiblingSubscribed() = runTest {
        val manager = DeviceManager()
        val stable = TestDevice()
        val groupRoot = TestDevice()
        val leaf = TestDevice()
        manager.registerDevice("stable", stable)
        manager.registerDeviceTree("group", DeviceTree(groupRoot, mapOf("leaf" to DeviceTree(leaf))))
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch { manager.messageFlow().collect { received.add(it) } }
        runCurrent()
        listOf(stable, groupRoot, leaf).forEach { assertEquals(1, it.events.subscriptionCount.value) }

        manager.registerDeviceTree("group", DeviceTree())
        runCurrent()
        assertEquals(0, groupRoot.events.subscriptionCount.value)
        assertEquals(0, leaf.events.subscriptionCount.value)
        assertEquals(1, stable.events.subscriptionCount.value)
        assertEquals(1, stable.subscriptionStarts)

        assertTrue(groupRoot.events.tryEmit(message("removed-root")))
        assertTrue(leaf.events.tryEmit(message("removed-leaf")))
        val event = message("stable-update")
        assertTrue(stable.events.tryEmit(event))
        runCurrent()
        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("stable"))), received)
    }

    @Test
    fun testChildFailureCancelsRootListener() = runTest {
        val stable = TestDevice()
        val failure = IllegalStateException("Child message flow failed")
        val failing = TestDevice(flow {
            stable.events.subscriptionCount.first { it == 1 }
            throw failure
        })
        val tree = DeviceTree(stable, mapOf("failing" to DeviceTree(failing)))
        val collector = backgroundScope.launch {
            val thrown = assertFailsWith<IllegalStateException> { tree.messageFlow().collect {} }
            assertEquals(failure.message, thrown.message)
        }
        runCurrent()

        assertTrue(collector.isCompleted)
        assertEquals(1, stable.subscriptionStarts)
        assertEquals(0, stable.events.subscriptionCount.value)
    }

    @Test
    fun testStaticTreeCompletes() = runTest {
        val event = message("finite-update")
        val tree = DeviceTree(children = mapOf("child" to DeviceTree(TestDevice(flowOf(event)))))
        val received = mutableListOf<DeviceMessage>()
        val collector = backgroundScope.launch { tree.messageFlow().collect { received.add(it) } }
        runCurrent()

        assertTrue(collector.isCompleted)
        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("child"))), received)
    }

    @Test
    fun testFiniteRootCompletes() = runTest {
        val event = message("finite-root-update")
        val tree = DeviceTree(TestDevice(flowOf(event)))
        val received = mutableListOf<DeviceMessage>()
        val collector = backgroundScope.launch { tree.messageFlow().collect { received.add(it) } }
        runCurrent()

        assertTrue(collector.isCompleted)
        assertEquals(listOf<DeviceMessage>(event), received)
    }

    @Test
    fun testEmptyTreeCompletes() = runTest {
        val collector = backgroundScope.launch { DeviceTree().messageFlow().collect {} }
        runCurrent()

        assertTrue(collector.isCompleted)
    }
}
