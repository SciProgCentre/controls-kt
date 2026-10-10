@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

internal class DeviceTreeCompositionTest {
    private class TestDevice(messages: Flow<DeviceMessage>? = null) : Device {
        val events = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 1)
        override val messageFlow: Flow<DeviceMessage> = messages ?: events
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

    @Test
    fun testCompositionChangeDuringInitialSubscriptionIsObserved() = runTest {
        val context = Context("tree-composition-initial-subscription") {
            coroutineContext(UnconfinedTestDispatcher(testScheduler))
            plugin(DeviceManager)
        }
        val manager = context.request(DeviceManager)
        val child = TestDevice()
        // the root device registers a child as soon as its messages are collected
        val tree = object : DeviceTree by manager {
            override val device: Device = TestDevice(flow {
                manager.registerDevice("child", child)
                awaitCancellation()
            })
        }
        val received = mutableListOf<DeviceMessage>()
        val collector = backgroundScope.launch { tree.deviceMessageFlow().collect { received.add(it) } }
        runCurrent()

        val event = PropertyChangedMessage(Instant.fromEpochMilliseconds(0), "value", Meta.EMPTY)
        child.events.emit(event)
        runCurrent()
        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("child"))), received)
        collector.cancelAndJoin()
        context.close()
    }

    @Test
    fun testDeviceRegisteredWhileCompositionFlowStartsIsObserved() = runTest {
        val context = Context("tree-composition-flow-start") {
            coroutineContext(UnconfinedTestDispatcher(testScheduler))
            plugin(DeviceManager)
        }
        val manager = context.request(DeviceManager)
        val child = TestDevice()
        val tree = object : DeviceTree by manager {
            override val treeMessageFlow = manager.treeMessageFlow.onStart {
                manager.registerDevice("child", child)
            }
        }
        val received = mutableListOf<DeviceMessage>()
        val collector = backgroundScope.launch { tree.deviceMessageFlow().collect { received.add(it) } }
        try {
            runCurrent()
            assertEquals(child, manager.children["child"]?.device)
            val event = PropertyChangedMessage(Instant.fromEpochMilliseconds(0), "value", Meta.EMPTY)
            child.events.emit(event)
            runCurrent()
            assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("child"))), received)
        } finally {
            collector.cancelAndJoin()
            context.close()
        }
    }

    @Test
    fun testDeviceRegisteredBeforeCompositionCollectorStartsIsObserved() = runTest {
        val context = Context("tree-composition-before-collector") {
            coroutineContext(UnconfinedTestDispatcher(testScheduler))
            plugin(DeviceManager)
        }
        val manager = context.request(DeviceManager)
        val child = TestDevice()
        val received = mutableListOf<DeviceMessage>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.deviceMessageFlow().collect { received.add(it) }
        }
        // runs after the flow producer and before the composition collector it launches
        backgroundScope.launch { manager.registerDevice("child", child) }
        runCurrent()

        val event = PropertyChangedMessage(Instant.fromEpochMilliseconds(0), "value", Meta.EMPTY)
        child.events.emit(event)
        runCurrent()
        assertEquals(listOf<DeviceMessage>(event.copy(sourceDevice = Name.of("child"))), received)
        collector.cancelAndJoin()
        context.close()
    }
}
