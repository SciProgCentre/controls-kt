@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package space.kscience.controls.constructor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.*
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import kotlin.test.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class DeviceConstructorMutationTest {
    private suspend fun TestScope.withDevice(block: suspend (DeviceConstructor) -> Unit) {
        val context = Context {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            block(DeviceConstructor(context))
        } finally {
            context.close()
        }
    }

    private fun TestScope.recordMessages(device: DeviceConstructor): MutableList<DeviceMessage> =
        mutableListOf<DeviceMessage>().also { messages ->
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                device.messageFlow.collect { messages.add(it) }
            }
        }

    private class LastInFirstOutDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.addLast(block)
        }

        fun drain() {
            while (pending.isNotEmpty()) pending.removeLast().run()
        }
    }

    @Test
    fun descriptionNotificationsKeepMutationOrder() = runTest(timeout = 5.seconds) {
        val dispatcher = LastInFirstOutDispatcher()
        val context = Context {
            coroutineContext(backgroundScope.coroutineContext + dispatcher)
        }
        try {
            val device = DeviceConstructor(context)
            val actionCounts = mutableListOf<Int>()
            val treeChanges = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
                device.messageFlow.collect { message ->
                    if (message is DescriptionMessage) actionCounts.add(message.actions.size)
                    if (message is DeviceTreeChildDeviceChangedMessage) treeChanges.add(message.childDeviceName)
                }
            }
            device.start()
            actionCounts.clear()

            repeat(3) { index ->
                device.registerAction(
                    MetaConverter.double, MetaConverter.double, ActionDescriptor("action-$index")
                ) { it }
            }
            device.installTree("first", DeviceTree())
            device.installTree("second", DeviceTree())
            val detach: (String) -> DeviceTree? = device::removeTree
            detach("first")
            dispatcher.drain()

            assertEquals(listOf(1, 2, 3), actionCounts)
            assertEquals(listOf("first", "second", "first"), treeChanges)
        } finally {
            context.close()
        }
    }

    @Test
    fun startPublishesInitialDescription() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            val messages = recordMessages(device)
            device.registerProperty("value", MetaConverter.double, ValueState(1.0))
            device.registerAction(MetaConverter.double, MetaConverter.double, ActionDescriptor("increment")) { it + 1 }
            runCurrent()
            assertTrue(messages.none { it is DescriptionMessage })

            device.start()

            val description = messages.filterIsInstance<DescriptionMessage>().single()
            assertEquals(listOf("value"), description.properties.map { it.name })
            assertEquals(listOf("increment"), description.actions.map { it.name })
            assertEquals(Name.EMPTY, description.sourceDevice)
        }
    }

    @Test
    fun unregisterPropertyStopsOnlyItsPublisher() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            val messages = recordMessages(device)
            val removed = MutableValueState(1.0)
            val retained = MutableValueState(10.0)
            val publisherStopping = CompletableDeferred<Unit>()
            val releasePublisher = CompletableDeferred<Unit>()
            val published = object : ValueState<Double> by removed {
                override fun subscribeWithTime(): Flow<ValueWithTime<Double>> =
                    removed.subscribeWithTime().onCompletion {
                        publisherStopping.complete(Unit)
                        withContext(NonCancellable) { releasePublisher.await() }
                    }
            }
            val externalValues = mutableListOf<Double>()
            backgroundScope.launch { removed.subscribe().collect { externalValues.add(it) } }
            device.registerProperty("removed", MetaConverter.double, published)
            device.registerProperty("retained", MetaConverter.double, retained)
            device.start()
            runCurrent()

            val removal = try {
                val removal = async { device.unregisterProperty("removed") }
                runCurrent()
                assertTrue(publisherStopping.isCompleted)
                assertFalse(removal.isCompleted)
                removal
            } finally {
                releasePublisher.complete(Unit)
            }
            assertTrue(removal.await())
            messages.clear()
            removed.value = 2.0
            retained.value = 20.0
            runCurrent()

            assertEquals(listOf(1.0, 2.0), externalValues)
            assertTrue(messages.filterIsInstance<PropertyChangedMessage>().none { it.property == "removed" })
            assertEquals(20.0, messages.filterIsInstance<PropertyChangedMessage>().last().value.double)
            assertTrue(device.coroutineContext[Job]!!.isActive)
        }
    }

    @Test
    fun unregisterPropertyRemovesRegistryEntries() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            device.registerProperty("removed", MetaConverter.double, ValueState(1.0))
            device.start()
            runCurrent()
            assertTrue(device.unregisterProperty("removed"))

            assertNull(device.getCachedProperty("removed"))
            assertFailsWith<IllegalStateException> { device.readProperty("removed") }
            assertFailsWith<IllegalStateException> { device.propertyAsState("removed", MetaConverter.double) }
            assertTrue(device.propertyDescriptors.none { it.name == "removed" })
            assertTrue(device.constructorElements.filterIsInstance<PropertyConstructorElement<*>>()
                .none { it.propertyName == "removed" })
            assertFalse(device.unregisterProperty("removed"))
        }
    }

    @Test
    fun unregisteredPropertyNameCanBeReused() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            device.registerProperty("group.value", MetaConverter.double, ValueState(1.0))
            assertTrue(device.unregisterProperty("group.value"))
            device.registerProperty("group.value", MetaConverter.double, ValueState(2.0))

            assertEquals(2.0, device.readProperty("group.value").double)
            assertEquals(1, device.constructorElements.filterIsInstance<PropertyConstructorElement<*>>().size)
        }
    }

    @Test
    fun unregisterActionDoesNotCancelAnExistingInvocation() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val direct = device.registerAction(
                MetaConverter.double, MetaConverter.double, ActionDescriptor("increment")
            ) { value ->
                entered.complete(Unit)
                release.await()
                value + 1
            }
            val invocation = async { device.execute("increment", Meta(2.0)) }
            entered.await()

            val unregister: (String) -> Boolean = device::unregisterAction
            assertTrue(unregister("increment"))
            assertFailsWith<IllegalStateException> { device.execute("increment", Meta(3.0)) }
            release.complete(Unit)

            assertEquals(3.0, invocation.await()?.double)
            assertEquals(4.0, direct(3.0))
            assertFalse(device.unregisterAction("increment"))
            assertTrue(device.actionDescriptors.isEmpty())
            device.registerAction(MetaConverter.double, MetaConverter.double, ActionDescriptor("increment")) { it + 2 }
            assertEquals(5.0, device.execute("increment", Meta(3.0))?.double)
        }
    }

    @Test
    fun descriptionChangesCaptureEachRegistrationAndRemoval() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            val messages = recordMessages(device)
            device.start()
            messages.clear()

            device.registerProperty("value", MetaConverter.double, MutableValueState(1.0))
            device.registerAction(MetaConverter.double, MetaConverter.double, ActionDescriptor("increment")) { it + 1 }
            device.unregisterProperty("value")
            device.unregisterAction("increment")
            runCurrent()

            val snapshots = messages.filterIsInstance<DescriptionMessage>().map { message ->
                message.properties.map { it.name } to message.actions.map { it.name }
            }
            assertEquals(listOf(
                listOf("value") to emptyList(),
                listOf("value") to listOf("increment"),
                emptyList<String>() to listOf("increment"),
                emptyList<String>() to emptyList(),
            ), snapshots)
            messages.clear()
            assertFalse(device.unregisterProperty("missing"))
            assertFalse(device.unregisterAction("missing"))
            runCurrent()
            assertTrue(messages.isEmpty())
        }
    }

    @Test
    fun removeTreeDetachesWithoutStoppingItsDevices() = runTest(timeout = 5.seconds) {
        withDevice { device ->
            val messages = recordMessages(device)
            val child = device.installTree("child", DeviceConstructor(device.context))
            val leaf = child.installTree("leaf", DeviceConstructor(device.context))
            device.start()
            runCurrent()
            messages.clear()

            assertSame(child, device.removeTree("child"))
            runCurrent()

            assertFalse(device.children.containsKey("child"))
            assertNull(device.resolveDeviceOrNull(Name.of("child")))
            assertTrue(device.descendantDevices().isEmpty())
            assertTrue(device.constructorElements.filterIsInstance<ChildConstructorElement>()
                .none { it.constructor === child })
            assertEquals(LifecycleState.STARTED, child.lifecycleState)
            assertEquals(LifecycleState.STARTED, leaf.lifecycleState)
            assertTrue(child.coroutineContext[Job]!!.isActive)
            assertTrue(leaf.coroutineContext[Job]!!.isActive)
            assertEquals(listOf("child"), messages.filterIsInstance<DeviceTreeChildDeviceChangedMessage>()
                .map { it.childDeviceName })
            messages.clear()
            assertNull(device.removeTree("child"))
            runCurrent()
            assertTrue(messages.isEmpty())
            device.installTree("child", child)
            assertSame(child, device.resolveDevice("child"))
            assertSame(leaf, device.resolveDevice("child.leaf"))
        }
    }
}
