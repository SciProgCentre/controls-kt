package space.kscience.controls.utilities

/*
 * LLM generated code: Comprehensive unit tests for Accumulator device in controls-utilities module.
 */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.nullable
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AccumulatorTest {

    private class TestSourceDevice(context: Context) : DeviceConstructor(context) {
        val flowRate: MutableValueState<Double?> by virtualProperty(
            MetaConverter.double.nullable(),
            initialState = 10.0
        )
    }

    private class CustomTimedState(
        initial: ValueWithTime<Double?>
    ) : ValueState<Double?> {
        val flow = MutableStateFlow(initial)

        override val valueWithTime: ValueWithTime<Double?> get() = flow.value
        override fun subscribeWithTime() = flow
        override fun toString(): String = "CustomTimedState($valueWithTime)"

        suspend fun emit(value: Double?, time: Instant) {
            flow.emit(ValueWithTime(value, time))
        }
    }

    private val testContext = Context("test")

    @Test
    fun testAccumulatorInitialStateNullValue() {
        val valueState = ValueState<Double?>(null)
        val accumulator = Accumulator(testContext, valueState, 10.seconds)

        assertEquals(0.0, accumulator.state.value)
    }

    @Test
    fun testAccumulatorInitialStateNonNullValue() = runTest(timeout = 1.seconds) {
        val valueState = ValueState<Double?>(25.0)
        val accumulator = Accumulator(testContext, valueState, 10.seconds, backgroundScope)

        assertEquals(25.0, accumulator.state.value)
    }

    @Test
    fun testAccumulatorIntegrationAndWindowExpiry() = runTest(timeout = 1.seconds) {
        val t0 = Instant.fromEpochMilliseconds(1000)
        val timedState = CustomTimedState(ValueWithTime(5.0, t0))
        val accumulator = Accumulator(testContext, timedState, 10.seconds, backgroundScope)

        // Initial sum is 5.0 at t=1000ms
        assertEquals(5.0, accumulator.state.value)

        // At t=3000ms (+2s), emit 10.0 -> window covers [0, 3000], sum = 5.0 + 10.0 = 15.0
        timedState.emit(10.0, t0 + 2.seconds)
        assertEquals(15.0, accumulator.state.subscribe().first { it == 15.0 })
        assertEquals(15.0, accumulator.state.value)

        // At t=6000ms (+5s), emit 20.0 -> window covers [0, 6000], sum = 5.0 + 10.0 + 20.0 = 35.0
        timedState.emit(20.0, t0 + 5.seconds)
        assertEquals(35.0, accumulator.state.subscribe().first { it == 35.0 })
        assertEquals(35.0, accumulator.state.value)

        // At t=12000ms (+11s), emit 1.0 -> window [2000ms, 12000ms] -> t0 (1000ms) has expired (< 2000ms)
        // Active values: 10.0 (at t0+2s) + 20.0 (at t0+5s) + 1.0 (at t0+11s) = 31.0
        timedState.emit(1.0, t0 + 11.seconds)
        assertEquals(31.0, accumulator.state.subscribe().first { it == 31.0 })
        assertEquals(31.0, accumulator.state.value)

        // At t=18000ms (+17s), emit 2.0 -> window [8000ms, 18000ms] -> t0+2s (3000ms) and t0+5s (6000ms) expired
        // Active values: 1.0 (at t0+11s) + 2.0 (at t0+17s) = 3.0
        timedState.emit(2.0, t0 + 17.seconds)
        assertEquals(3.0, accumulator.state.subscribe().first { it == 3.0 })
        assertEquals(3.0, accumulator.state.value)

        // At t=30000ms (+29s), emit 7.0 -> window [20000ms, 30000ms] -> all previous expired
        // Active values: 7.0 (at t0+29s) = 7.0
        timedState.emit(7.0, t0 + 29.seconds)
        assertEquals(7.0, accumulator.state.subscribe().first { it == 7.0 })
        assertEquals(7.0, accumulator.state.value)
    }

    @Test
    fun testAccumulatorIgnoresNullValues() = runTest(timeout = 1.seconds) {
        val t0 = Instant.fromEpochMilliseconds(1000)
        val timedState = CustomTimedState(ValueWithTime(10.0, t0))
        val accumulator = Accumulator(testContext, timedState, 10.seconds, backgroundScope)

        assertEquals(10.0, accumulator.state.value)

        // Emit null at t0 + 2s -> null ignored during integration, sum remains 10.0
        timedState.emit(null, t0 + 2.seconds)
        assertEquals(10.0, accumulator.state.value)

        // Emit 15.0 at t0 + 4s -> sum = 10.0 + 15.0 = 25.0
        timedState.emit(15.0, t0 + 4.seconds)
        assertEquals(25.0, accumulator.state.subscribe().first { it == 25.0 })
        assertEquals(25.0, accumulator.state.value)

        // Emit null at t0 + 12s -> window [3000ms, 13000ms] -> t0 (1000ms) expired, null not added
        // Remaining: 15.0 (at t0+4s) -> sum = 15.0
        timedState.emit(null, t0 + 12.seconds)
        assertEquals(15.0, accumulator.state.subscribe().first { it == 15.0 })
        assertEquals(15.0, accumulator.state.value)

        // Emit null at t0 + 20s -> window [11000ms, 21000ms] -> all values expired
        // Remaining: none -> sum = 0.0
        timedState.emit(null, t0 + 20.seconds)
        assertEquals(0.0, accumulator.state.subscribe().first { it == 0.0 })
        assertEquals(0.0, accumulator.state.value)
    }

    @Test
    fun testAccumulatorRegisteredProperty() = runTest(timeout = 1.seconds) {
        val valueState = MutableValueState<Double?>(50.0)
        val accumulator = Accumulator(testContext, valueState, 10.seconds)

        val meta = accumulator.readProperty("state")
        assertEquals(50.0, meta.double)

        valueState.value = 30.0
        assertEquals(80.0, accumulator.state.subscribe().first { it == 80.0 })
        val updatedMeta = accumulator.readProperty("state")
        assertEquals(80.0, updatedMeta.double)
    }

    @Test
    fun testAccumulatorFactoryBuildDevice() = runTest(timeout = 1.seconds) {
        val context = Context("factoryTest") {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)
        val sourceDevice = deviceManager.install("sensor", TestSourceDevice(context))

        val meta = Meta {
            "deviceName" put "sensor"
            "propertyName" put "flowRate"
            "window" put "5s"
        }

        val accumulatorDevice = deviceManager.install(Accumulator.buildDevice(context, meta))

        assertEquals(10.0, accumulatorDevice.state.value)

        sourceDevice.flowRate.value = 20.0
        assertEquals(30.0, accumulatorDevice.state.subscribe().first { it == 30.0 })
        assertEquals(30.0, accumulatorDevice.state.value)
    }

    @Test
    fun testAccumulatorFactoryWithDurationString() = runTest {
        val context = Context("factoryDurationStringTest") {
            plugin(DeviceManager)
        }
        val deviceManager = context.request(DeviceManager)
        val sourceDevice = deviceManager.install("sensor", TestSourceDevice(context))

        val meta = Meta {
            "deviceName" put "sensor"
            "propertyName" put "flowRate"
            "window" put "5s"
        }

        val accumulatorDevice = Accumulator.buildDevice(context, meta)
        assertEquals(10.0, accumulatorDevice.state.value)
        assertEquals(5.seconds, accumulatorDevice.window)
    }

    @Test
    fun testAccumulatorFactoryWithDottedDeviceName() = runTest(timeout = 1.seconds) {
        val context = Context("factoryDottedTest") {
            plugin(DeviceManager)
            coroutineContext(backgroundScope.coroutineContext)
        }
        val deviceManager = context.request(DeviceManager)
        val group = deviceManager.install("group", DeviceConstructor(context))
        val sourceDevice = group.install("sensor", TestSourceDevice(context))

        val meta = Meta {
            "deviceName" put "group.sensor"
            "propertyName" put "flowRate"
            "window" put 10.0
        }

        val accumulatorDevice = Accumulator.buildDevice(context, meta)
        assertEquals(10.0, accumulatorDevice.state.value)

        sourceDevice.flowRate.value = 15.0
        assertEquals(25.0, accumulatorDevice.state.subscribe().first { it == 25.0 })
        assertEquals(25.0, accumulatorDevice.state.value)
    }

    @Test
    fun testAccumulatorFactoryMissingParameters() {
        val context = Context("factoryTest") {
            plugin(DeviceManager)
        }

        assertFailsWith<IllegalStateException> {
            Accumulator.buildDevice(context, Meta {
                "propertyName" put "flowRate"
                "window" put 5.0
            })
        }

        assertFailsWith<IllegalStateException> {
            Accumulator.buildDevice(context, Meta {
                "deviceName" put "sensor"
                "window" put 5.0
            })
        }

        assertFailsWith<IllegalStateException> {
            Accumulator.buildDevice(context, Meta {
                "deviceName" put "sensor"
                "propertyName" put "flowRate"
            })
        }

        val emptyContext = Context("empty")
        assertFailsWith<IllegalStateException> {
            Accumulator.buildDevice(emptyContext, Meta {
                "deviceName" put "sensor"
                "propertyName" put "flowRate"
                "window" put 5.0
            })
        }
    }

    @Test
    fun testAccumulatorFromPlugin() {
        val context = Context("pluginTest") {
            plugin(DeviceManager)
            plugin(ControlsUtilitiesPlugin)
        }
        val plugin = context.request(ControlsUtilitiesPlugin)
        val factories = plugin.content(DeviceManager.DEVICE_FACTORY_TARGET)
        assertEquals(Accumulator, factories[space.kscience.dataforge.names.Name.of("accumulator")])
    }
}
