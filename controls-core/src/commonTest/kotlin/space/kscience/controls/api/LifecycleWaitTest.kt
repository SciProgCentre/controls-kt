package space.kscience.controls.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class LifecycleWaitTest {
    private class TestDevice(var state: LifecycleState, onSubscription: (TestDevice.() -> Unit)? = null) : Device {
        val events = MutableSharedFlow<DeviceMessage>()
        override val messageFlow: Flow<DeviceMessage> =
            if (onSubscription == null) events else events.onStart { onSubscription() }
        override val lifecycleState: LifecycleState get() = state
        override val propertyDescriptors: Collection<PropertyDescriptor> = emptyList()
        override val actionDescriptors: Collection<ActionDescriptor> = emptyList()
        override val context: Context get() = error("Not used by this fixture")
        override val clock: Clock = Clock.System
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        override suspend fun readProperty(propertyName: String): Meta = Meta.EMPTY
        override suspend fun writeProperty(propertyName: String, value: Meta) {}
        override suspend fun execute(actionName: String, argument: Meta?): Meta? = null

        suspend fun change(newState: LifecycleState) {
            state = newState
            events.emit(DeviceLifeCycleMessage(Instant.DISTANT_PAST, newState, Name.EMPTY))
        }
    }

    @Test
    fun testReachedStateReturnsWithoutSubscribing() = runTest {
        val device = TestDevice(LifecycleState.STARTED)
        device.awaitLifecycleState(LifecycleState.STARTED)
        assertEquals(0, device.events.subscriptionCount.value)
    }

    @Test
    fun testTransitionDuringSubscriptionIsNotMissed() = runTest {
        // the device starts while the message flow is being subscribed to, and nobody receives the event yet
        val device = TestDevice(LifecycleState.STOPPED) { state = LifecycleState.STARTED }
        withTimeout(1.seconds) { device.awaitLifecycleState(LifecycleState.STARTED) }
    }

    @Test
    fun testShortTransitionIsObservedByItsEvent() = runTest {
        val device = TestDevice(LifecycleState.STOPPED)
        var reached = false
        backgroundScope.launch {
            device.awaitLifecycleState(LifecycleState.STARTING)
            reached = true
        }
        runCurrent()
        device.change(LifecycleState.STARTING)
        device.change(LifecycleState.STARTED)
        runCurrent()
        assertTrue(reached)
        assertEquals(0, device.events.subscriptionCount.value)
    }

    @Test
    fun testCancellationReleasesTheSubscription() = runTest {
        val device = TestDevice(LifecycleState.STOPPED)
        val waiting = backgroundScope.launch { device.awaitLifecycleState(LifecycleState.STARTED) }
        runCurrent()
        assertEquals(1, device.events.subscriptionCount.value)
        waiting.cancel()
        runCurrent()
        assertEquals(0, device.events.subscriptionCount.value)
    }
}
