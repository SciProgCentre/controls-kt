@file:OptIn(ExperimentalCoroutinesApi::class)

package space.kscience.controls.constructor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.messageFlow
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals

internal class DeviceTreeMessageFlowTest {
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
            runCurrent()

            val received = mutableListOf<PropertyChangedMessage>()
            backgroundScope.launch {
                root.messageFlow().filterIsInstance<PropertyChangedMessage>().collect { received.add(it) }
            }
            runCurrent()
            state.value = 1.0
            runCurrent()

            root.installTree("child", DeviceConstructor(context))
            runCurrent()
            state.value = 2.0
            runCurrent()

            assertEquals(2, received.size)
            received.forEach {
                assertEquals(Name.EMPTY, it.sourceDevice)
                assertEquals("value", it.property)
            }
            assertEquals(1.0, received[0].value.double)
            assertEquals(2.0, received[1].value.double)
        } finally {
            context.close()
        }
    }
}
