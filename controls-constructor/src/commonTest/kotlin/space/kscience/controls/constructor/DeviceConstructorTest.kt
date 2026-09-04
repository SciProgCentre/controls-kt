package space.kscience.controls.constructor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DeviceConstructorTest {

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
    fun testPropertyMessageUsesClockForUntimedSource() = runTest(timeout = 5.seconds) {
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

            assertNotEquals(Instant.DISTANT_PAST, message.time)
            assertEquals(1.0, message.value.double)
        } finally {
            context.close()
        }
    }
}
