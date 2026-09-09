package space.kscience.controls.constructor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.DeviceTree
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.api.start
import space.kscience.controls.api.valueType
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.double
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DeviceConstructorTest {

    private fun CoroutineScope.recordLifecycleStates(device: Device): List<LifecycleState> {
        val states = mutableListOf<LifecycleState>()
        launch(start = CoroutineStart.UNDISPATCHED) {
            device.messageFlow.filterIsInstance<DeviceLifeCycleMessage>().collect { states += it.state }
        }
        return states
    }

    @Test
    fun testStartDoesNothingWhenStarted() = runTest(timeout = 5.seconds) {
        val context = Context("constructor-repeated-start") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val parent = DeviceConstructor(context)
            val child = parent.installTree("child", DeviceConstructor(context))
            val parentStates = backgroundScope.recordLifecycleStates(parent)
            val childStates = backgroundScope.recordLifecycleStates(child)

            parent.start()
            parent.start()

            val expected = listOf(LifecycleState.STARTING, LifecycleState.STARTED)
            assertEquals(expected, parentStates)
            assertEquals(expected, childStates)
            assertEquals(LifecycleState.STARTED, parent.lifecycleState)
            assertEquals(LifecycleState.STARTED, child.lifecycleState)
        } finally {
            context.close()
        }
    }

    @Test
    fun testStartDoesNothingWhileStarting() = runTest(timeout = 5.seconds) {
        val context = Context("constructor-starting-reentry") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val parent = DeviceConstructor(context)
            val child = parent.installTree("child", object : DeviceConstructor(context) {
                override suspend fun start() {
                    if (entered.complete(Unit)) release.await()
                    super.start()
                }
            })
            val parentStates = backgroundScope.recordLifecycleStates(parent)
            val childStates = backgroundScope.recordLifecycleStates(child)
            val firstStart = launch { parent.start() }
            try {
                entered.await()
                assertEquals(LifecycleState.STARTING, parent.lifecycleState)

                parent.start()

                assertEquals(LifecycleState.STARTING, parent.lifecycleState)
                assertEquals(LifecycleState.STOPPED, child.lifecycleState)
                assertFalse(firstStart.isCompleted)
                release.complete(Unit)
                firstStart.join()

                val expected = listOf(LifecycleState.STARTING, LifecycleState.STARTED)
                assertEquals(expected, parentStates)
                assertEquals(expected, childStates)
                assertEquals(LifecycleState.STARTED, parent.lifecycleState)
                assertEquals(LifecycleState.STARTED, child.lifecycleState)
            } finally {
                release.complete(Unit)
                firstStart.cancelAndJoin()
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun testNestedTreeStartsOnce() = runTest(timeout = 5.seconds) {
        val context = Context("constructor-nested-start") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val parent = DeviceConstructor(context)
            val child = parent.installTree("child", DeviceConstructor(context))
            val leaf = child.installTree("leaf", DeviceConstructor(context))
            val devices = listOf(parent, child, leaf)
            val states = devices.map { backgroundScope.recordLifecycleStates(it) }
            val tree: DeviceTree = parent

            coroutineScope { tree.start().join() }

            val expected = listOf(LifecycleState.STARTING, LifecycleState.STARTED)
            states.forEach { assertEquals(expected, it) }
            devices.forEach { assertEquals(LifecycleState.STARTED, it.lifecycleState) }
        } finally {
            context.close()
        }
    }

    private class CustomTimedState(override val valueWithTime: ValueWithTime<Double>) : ValueState<Double> {
        override fun subscribeWithTime(): Flow<ValueWithTime<Double>> = flowOf(valueWithTime)

        override fun toString(): String = "CustomTimedState($valueWithTime)"
    }

    @Test
    fun testPropertyMessageKeepsSourceTime() = runTest(timeout = 5.seconds) {
        val context = Context("property-source-time") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = DeviceConstructor(context)
            val t0 = Instant.fromEpochSeconds(1_000)
            val source = CustomTimedState(ValueWithTime(1.0, t0))
            val received = CompletableDeferred<PropertyChangedMessage>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                received.complete(device.messageFlow.filterIsInstance<PropertyChangedMessage>().first {
                    it.property == "value"
                })
            }

            device.registerProperty(name = "value", converter = MetaConverter.double, state = source)
            val message = received.await()

            assertEquals(t0, message.time)
            assertEquals(1.0, message.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testPropertyMessageKeepsUnknownTime() = runTest(timeout = 5.seconds) {
        val context = Context("property-untimed-source") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = DeviceConstructor(context)
            val received = CompletableDeferred<PropertyChangedMessage>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                received.complete(device.messageFlow.filterIsInstance<PropertyChangedMessage>().first {
                    it.property == "value"
                })
            }

            device.registerProperty(name = "value", converter = MetaConverter.double, state = ValueState(1.0))
            val message = received.await()

            assertEquals(Instant.DISTANT_PAST, message.time)
            assertEquals(1.0, message.value.double)
        } finally {
            context.close()
        }
    }

    @Test
    fun testPropertyDescriptorsUseConverter() = runTest(timeout = 5.seconds) {
        val context = Context("property-converter-descriptors") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = object : DeviceConstructor(context) {
                val virtual by virtualProperty(MetaConverter.double, 1.0)
            }
            device.registerProperty("readOnly", MetaConverter.double, ValueState(1.0))
            device.registerMutableProperty("mutable", MetaConverter.double, MutableValueState(1.0))

            for (name in listOf("readOnly", "mutable", "virtual")) {
                val descriptor = device.propertyDescriptors.single { it.name == name }
                assertEquals(listOf(ValueType.NUMBER), descriptor.metaDescriptor.valueTypes, name)
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun testPropertyDescriptorBuilderOverridesConverter() = runTest(timeout = 5.seconds) {
        val context = Context("property-descriptor-overrides") {
            coroutineContext(backgroundScope.coroutineContext)
        }
        try {
            val device = object : DeviceConstructor(context) {
                val virtual by virtualProperty(
                    MetaConverter.double,
                    1.0,
                    descriptorBuilder = { valueType(ValueType.STRING) },
                )
            }
            device.registerProperty("readOnly", MetaConverter.double, ValueState(1.0)) {
                valueType(ValueType.STRING)
            }
            device.registerMutableProperty("mutable", MetaConverter.double, MutableValueState(1.0)) {
                valueType(ValueType.STRING)
            }

            for (name in listOf("readOnly", "mutable", "virtual")) {
                val descriptor = device.propertyDescriptors.single { it.name == name }
                assertEquals(listOf(ValueType.STRING), descriptor.metaDescriptor.valueTypes, name)
            }
        } finally {
            context.close()
        }
    }
}
