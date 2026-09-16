@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.constructor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.EmptyDeviceMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.messageFlow
import space.kscience.controls.spec.DeviceTreeSpec
import space.kscience.controls.spec.verifiedWith
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DeviceTreeMessageFlowTest {
    @Test
    fun testPublishedChildHasConstructorElement() = runTest {
        val context = Context("constructor-publication") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            val child = DeviceConstructor(context)
            var observed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                root.messageFlow.filterIsInstance<EmptyDeviceMessage>().first()
                observed = root.constructorElements
                    .filterIsInstance<ChildConstructorElement>()
                    .any { it.constructor === child }
            }
            root.installTree("child", child)
            runCurrent()
            assertTrue(observed, "The published child has no constructor element")
        } finally {
            context.close()
        }
    }

    @Test
    fun testLateNestedChild() = runTest {
        val context = Context("late-nested-child") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            val manager = DeviceManager()
            manager.registerDevice("root", root)
            val received = mutableListOf<PropertyChangedMessage>()
            backgroundScope.launch {
                manager.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
            }
            runCurrent()

            val group = root.installTree("group", DeviceConstructor(context))
            runCurrent()
            val leaf = group.installTree("leaf", DeviceConstructor(context))
            runCurrent()

            val state = MutableValueState(0.0)
            leaf.registerMutableProperty("value", MetaConverter.double, state)
            runCurrent()
            received.clear()
            state.value = 1.0
            runCurrent()

            val event = received.single()
            assertEquals(Name.of("root", "group", "leaf"), event.sourceDevice)
            assertEquals("value", event.property)
            assertEquals(1.0, event.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testOwnPropertyListenerSurvivesChildInstall() = runTest {
        val context = Context("property-listener-child-install") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            val state = MutableValueState(0.0)
            root.registerMutableProperty("value", MetaConverter.double, state)
            val childState = MutableValueState(0.0)
            val child = DeviceConstructor(context).apply {
                registerMutableProperty("childValue", MetaConverter.double, childState)
            }
            runCurrent()

            val received = mutableListOf<PropertyChangedMessage>()
            backgroundScope.launch {
                root.verifiedWith<DeviceTreeSpec>(DeviceTreeSpec()).messageFlow()
                    .filterIsInstance<PropertyChangedMessage>()
                    .collect { received.add(it) }
            }
            runCurrent()
            state.value = 1.0
            runCurrent()

            root.installTree("child", child)
            runCurrent()
            childState.value = 1.0
            runCurrent()
            state.value = 2.0
            runCurrent()

            assertEquals(3, received.size)
            assertEquals(Name.EMPTY, received[0].sourceDevice)
            assertEquals("value", received[0].property)
            assertEquals(1.0, received[0].value.double)
            assertEquals(Name.of("child"), received[1].sourceDevice)
            assertEquals("childValue", received[1].property)
            assertEquals(1.0, received[1].value.double)
            assertEquals(Name.EMPTY, received[2].sourceDevice)
            assertEquals("value", received[2].property)
            assertEquals(2.0, received[2].value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testResolvePropertyStateUsesReplayedTreeChange() = runTest {
        val context = Context("resolve-replayed-tree-change") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            val resolved = root.resolvePropertyState(Name.of("child"), "value")
            val child = DeviceConstructor(context).apply {
                registerMutableProperty("value", MetaConverter.double, MutableValueState(1.0))
            }

            root.installTree("child", child)
            runCurrent()

            assertEquals(1.0, resolved.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testResolvePropertyStateBindsStoppedChild() = runTest {
        val context = Context("resolve-stopped-child") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            val resolved = root.resolvePropertyState(Name.of("child"), "value")
            runCurrent()
            val child = DeviceConstructor(context).apply {
                registerMutableProperty("value", MetaConverter.double, MutableValueState(2.0))
            }

            root.installTree("child", child)
            runCurrent()

            assertEquals(LifecycleState.STOPPED, child.lifecycleState)
            assertEquals(2.0, resolved.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testResolvePropertyStateDoesNotDependOnFastStartedEvent() = runTest {
        val context = Context("resolve-fast-started-child") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            root.start()
            val resolved = root.resolvePropertyState(Name.of("child"), "value")
            val child = DeviceConstructor(context).apply {
                registerMutableProperty("value", MetaConverter.double, MutableValueState(3.0))
            }

            root.installTree("child", child)
            child.start()
            assertEquals(LifecycleState.STARTED, child.lifecycleState)
            runCurrent()

            assertEquals(3.0, resolved.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testResolvePropertyStateFindsNestedChildFromParentHint() = runTest {
        val context = Context("resolve-nested-child") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val root = DeviceConstructor(context)
            val resolved = root.resolvePropertyState(Name.of("group", "leaf"), "value")
            val group = DeviceConstructor(context)
            group.installTree("leaf", DeviceConstructor(context).apply {
                registerMutableProperty("value", MetaConverter.double, MutableValueState(4.0))
            })

            root.installTree("group", group)
            runCurrent()

            assertEquals(4.0, resolved.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testTreeChangesUseOnePublisherOnReentrantInstall() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = Context("conflated-tree-change-publisher") {
            coroutineContext(backgroundScope.coroutineContext + dispatcher)
        }
        try {
            val root = DeviceConstructor(context)
            val firstDelivery = CompletableDeferred<Unit>()
            val releaseCollector = CompletableDeferred<Unit>()
            val laterTreeSize = CompletableDeferred<Int>()
            var deliveries = 0
            backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                root.messageFlow.filterIsInstance<EmptyDeviceMessage>().collect {
                    deliveries++
                    if (deliveries == 1) {
                        root.installTree("second", DeviceConstructor(context))
                        firstDelivery.complete(Unit)
                        releaseCollector.await()
                    } else {
                        laterTreeSize.complete(root.children.size)
                    }
                }
            }

            root.installTree("first", DeviceConstructor(context))
            assertTrue(firstDelivery.isCompleted, "First hint was not delivered")
            firstDelivery.await()
            root.installTree("third", DeviceConstructor(context))
            assertEquals(1, root.coroutineContext.job.children.count { it.isActive })
            releaseCollector.complete(Unit)
            runCurrent()
            assertTrue(laterTreeSize.isCompleted, "Latest hint was not delivered")

            assertEquals(3, laterTreeSize.await())
        } finally {
            context.close()
        }
    }
}
