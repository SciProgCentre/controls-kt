@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.manager

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.EmptyDeviceMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.controls.spec.DeviceTreeSpec
import space.kscience.controls.spec.verifiedWith
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun testLateRegistration() = runTest {
        val manager = DeviceManager()
        val received = mutableListOf<DeviceMessage>()
        val collector = backgroundScope.launch {
            manager.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
        }
        runCurrent()
        assertFalse(collector.isCompleted)

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
        backgroundScope.launch {
            manager.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
        }
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
    fun testNestedRegistrationPrefixesFullPaths() = runTest {
        val manager = DeviceManager()
        val nestedManager = DeviceManager()
        manager.registerDeviceTree("group", nestedManager)
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch {
            manager.messageFlow().collect { received.add(it) }
        }
        runCurrent()
        received.clear()

        val root = TestDevice()
        val leaf = TestDevice()
        nestedManager.registerDeviceTree(
            "static",
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

        val device = TestDevice()
        nestedManager.registerDevice("late", device)
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)

        val event = message("nested-update")
        assertTrue(device.events.tryEmit(event))
        runCurrent()

        assertEquals(
            listOf<DeviceMessage>(
                rootEvent.copy(sourceDevice = Name.of("group", "static")),
                leafEvent.copy(sourceDevice = Name.of("group", "static", "nested", "leaf")),
                event.copy(sourceDevice = Name.of("group", "late")),
            ),
            received.filterIsInstance<PropertyChangedMessage>(),
        )
        val hints = received.filterIsInstance<EmptyDeviceMessage>()
        assertEquals(listOf(Name.of("group"), Name.of("group")), hints.map { it.sourceDevice })
        val encodedHint = Json.encodeToString<DeviceMessage>(hints.first())
        assertEquals(hints.first(), Json.decodeFromString<DeviceMessage>(encodedHint))
    }

    @Test
    fun testVerifiedManagerKeepsTreeChangeSource() = runTest {
        val manager = DeviceManager()
        val tree = manager.verifiedWith<DeviceTreeSpec>(DeviceTreeSpec())
        val received = mutableListOf<PropertyChangedMessage>()
        backgroundScope.launch {
            tree.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
        }
        runCurrent()

        val device = TestDevice()
        manager.registerDevice("late", device)
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)

        val event = message("verified-update")
        assertTrue(device.events.tryEmit(event))
        runCurrent()
        assertEquals(listOf(event.copy(sourceDevice = Name.of("late"))), received)
    }

    @Test
    fun testTreeIsReadAtCollection() = runTest {
        val manager = DeviceManager()
        val messages = manager.messageFlow()
        val device = TestDevice()
        manager.registerDevice("late", device)

        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch {
            messages.filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
        }
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)

        val event = message("current-tree-update")
        assertTrue(device.events.tryEmit(event))
        runCurrent()

        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("late"))), received)
    }

    @Test
    fun testOrdinaryMessagesDoNotRereadChildren() = runTest {
        val device = TestDevice()
        var childrenReads = 0
        val tree = object : DeviceTree {
            override val device: Device = device
            override val children: Map<String, DeviceTree>
                get() {
                    childrenReads++
                    return emptyMap()
                }
        }
        val received = mutableListOf<PropertyChangedMessage>()
        backgroundScope.launch {
            tree.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
        }
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)
        val initialChildrenReads = childrenReads

        val events = List(5) { message("update-$it") }
        events.forEach { event ->
            device.events.emit(event)
            runCurrent()
        }

        assertEquals(initialChildrenReads, childrenReads)
        assertEquals(events, received)
    }

    @Test
    fun testRepeatedRegistrationAndAdditionKeepSiblingSubscribed() = runTest {
        val manager = DeviceManager()
        val stable = TestDevice()
        val stableTree = DeviceTree(stable)
        manager.registerDeviceTree("stable", stableTree)
        val received = mutableListOf<DeviceMessage>()
        backgroundScope.launch {
            manager.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
        }
        runCurrent()
        assertEquals(1, stable.events.subscriptionCount.value)
        assertEquals(1, stable.subscriptionStarts)

        repeat(3) {
            manager.registerDeviceTree("stable", stableTree)
            runCurrent()
        }
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
    fun testStaticTreesComplete() = runTest {
        suspend fun assertCompleted(tree: DeviceTree, expected: List<DeviceMessage>) {
            val received = mutableListOf<DeviceMessage>()
            val collector = backgroundScope.launch { tree.messageFlow().collect { received.add(it) } }
            runCurrent()
            assertTrue(collector.isCompleted)
            assertEquals(expected, received)
        }

        assertCompleted(DeviceTree(), emptyList())
        val rootEvent = message("finite-root-update")
        assertCompleted(DeviceTree(TestDevice(flowOf(rootEvent))), listOf(rootEvent))
        val childEvent = message("finite-child-update")
        assertCompleted(
            DeviceTree(children = mapOf("child" to DeviceTree(TestDevice(flowOf(childEvent))))),
            listOf(childEvent.copy(sourceDevice = Name.of("child"))),
        )
    }
}
