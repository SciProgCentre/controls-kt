package space.kscience.controls.utilities

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.nullable
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextBuilder
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.descriptors.MetaValidationResult
import space.kscience.dataforge.meta.descriptors.validate
import space.kscience.dataforge.meta.descriptors.validateWithResult
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccumulatorTest {

    private class CustomTimedState(initial: ValueWithTime<Double?>) : ValueState<Double?> {
        private val flow = MutableStateFlow(initial)

        override val valueWithTime: ValueWithTime<Double?> get() = flow.value
        override fun subscribeWithTime() = flow
        override fun toString(): String = "CustomTimedState($valueWithTime)"

        suspend fun emit(value: Double?, time: Instant) {
            flow.emit(ValueWithTime(value, time))
        }
    }

    private fun ValueState<Double?>.asMeta(): ValueState<Meta> = map(MetaConverter.double.nullable()::convert)

    private suspend fun TestScope.withTestContext(
        name: String,
        configure: ContextBuilder.() -> Unit = {},
        block: suspend (Context) -> Unit,
    ) {
        val context = Context(name) {
            coroutineContext(backgroundScope.coroutineContext)
            configure()
        }
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    private suspend fun Accumulator.awaitState(value: Double, time: Instant) {
        val sample = state.subscribeWithTime().first { it.time == time }
        assertEquals(ValueWithTime(value, time), sample)
        assertEquals(sample, state.valueWithTime)
    }

    @Test
    fun testAccumulatorParameterDescriptor() {
        val descriptor = Accumulator.descriptor
        assertTrue(descriptor.validate(Meta { "window" put "5s" }))
        assertTrue(descriptor.validate(Meta { "window" put "PT5S" }))
        assertTrue(descriptor.validate(Meta { "window" put 5.0 }))

        assertFalse(descriptor.validate(Meta.EMPTY))
        val missingWindow = descriptor.validateWithResult(Meta.EMPTY, Name.EMPTY)
            .filterIsInstance<MetaValidationResult.RequiredValueIsMissing>().single()
        assertEquals(Name.of("window"), missingWindow.name)

        val wrongType = Meta { "window" put true }
        assertFalse(descriptor.validate(wrongType))
        val incorrectType = descriptor.validateWithResult(wrongType, Name.EMPTY)
            .filterIsInstance<MetaValidationResult.IncorrectValueType>().single()
        assertEquals(Name.of("window"), incorrectType.name)
        assertEquals(ValueType.BOOLEAN, incorrectType.actualType)
    }

    @Test
    fun testAccumulatorUnboundState() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorUnbound") { context ->
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            val initial = ValueWithTime(0.0, Instant.DISTANT_PAST)
            assertEquals(initial, accumulator.state.valueWithTime)
            runCurrent()
            assertEquals(initial, accumulator.state.valueWithTime)
        }
    }

    @Test
    fun testAccumulatorInitialStateNullValue() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorInitialNull") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(null, t0))
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            accumulator.bind(source.asMeta())
            accumulator.awaitState(0.0, t0)
        }
    }

    @Test
    fun testAccumulatorInitialStateNonNullValue() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorInitialValue") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(25.0, t0))
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            accumulator.bind(source.asMeta())
            accumulator.awaitState(25.0, t0)
        }
    }

    @Test
    fun testAccumulatorStartsFromZero() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorStartsFromZero") { context ->
            val source = CustomTimedState(ValueWithTime(25.0, Instant.DISTANT_PAST))
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            val initial = accumulator.state.valueWithTime
            accumulator.bind(source.asMeta())
            runCurrent()
            assertEquals(ValueWithTime(0.0, Instant.DISTANT_PAST), initial)
            assertEquals(initial, accumulator.state.valueWithTime)

            val t1 = Instant.fromEpochMilliseconds(1000)
            source.emit(10.0, t1)
            accumulator.awaitState(10.0, t1)
        }
    }

    @Test
    fun testAccumulatorIntegrationAndWindowExpiry() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorWindow") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(5.0, t0))
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            accumulator.bind(source.asMeta())
            accumulator.awaitState(5.0, t0)

            source.emit(10.0, t0 + 2.seconds)
            accumulator.awaitState(15.0, t0 + 2.seconds)

            source.emit(20.0, t0 + 5.seconds)
            accumulator.awaitState(35.0, t0 + 5.seconds)

            source.emit(1.0, t0 + 11.seconds)
            accumulator.awaitState(31.0, t0 + 11.seconds)

            source.emit(2.0, t0 + 17.seconds)
            accumulator.awaitState(3.0, t0 + 17.seconds)

            source.emit(7.0, t0 + 29.seconds)
            accumulator.awaitState(7.0, t0 + 29.seconds)
        }
    }

    @Test
    fun testAccumulatorNullAdvancesWindow() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorNullWindow") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(10.0, t0))
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            accumulator.bind(source.asMeta())
            accumulator.awaitState(10.0, t0)

            source.emit(null, t0 + 2.seconds)
            accumulator.awaitState(10.0, t0 + 2.seconds)

            source.emit(15.0, t0 + 4.seconds)
            accumulator.awaitState(25.0, t0 + 4.seconds)

            source.emit(null, t0 + 12.seconds)
            accumulator.awaitState(15.0, t0 + 12.seconds)

            source.emit(null, t0 + 20.seconds)
            accumulator.awaitState(0.0, t0 + 20.seconds)
        }
    }

    @Test
    fun testAccumulatorRegisteredProperty() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorRegisteredProperty") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(50.0, t0))
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            accumulator.bind(source.asMeta())
            accumulator.awaitState(50.0, t0)
            assertEquals(50.0, accumulator.readProperty("state").double)

            source.emit(30.0, t0 + 1.seconds)
            accumulator.awaitState(80.0, t0 + 1.seconds)
            assertEquals(80.0, accumulator.readProperty("state").double)
        }
    }

    @Test
    fun testAccumulatorFactoryBuildDevice() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorFactory") { context ->
            val parameters = Meta { "window" put 5.0 }
            assertEquals(5.seconds, parameters[Accumulator.Spec.window])
            val accumulator = Accumulator.buildDevice(context, parameters)
            assertEquals(5.seconds, accumulator.window)
            assertEquals(0.0, accumulator.state.value)

            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(10.0, t0))
            accumulator.bind(source.asMeta())
            accumulator.awaitState(10.0, t0)

            source.emit(20.0, t0 + 1.seconds)
            accumulator.awaitState(30.0, t0 + 1.seconds)
        }
    }

    @Test
    fun testAccumulatorFactoryWithDurationString() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorDurationString") { context ->
            val accumulator = Accumulator.buildDevice(context, Meta { "window" put "5s" })
            assertEquals(5.seconds, accumulator.window)
            assertEquals(0.0, accumulator.state.value)
        }
    }

    @Test
    fun testAccumulatorFactoryWithDurationNumber() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorDurationNumber") { context ->
            val accumulator = Accumulator.buildDevice(context, Meta { "window" put 10.0 })
            assertEquals(10.seconds, accumulator.window)
            assertEquals(0.0, accumulator.state.value)
        }
    }

    @Test
    fun testAccumulatorFactoryMissingWindow() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorMissingWindow") { context ->
            assertFailsWith<IllegalStateException> {
                Accumulator.buildDevice(context, Meta.EMPTY)
            }
        }
    }

    @Test
    fun testAccumulatorBindingInputNames() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorInputNames") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(10.0, t0))
            val defaultInput = Accumulator(context, 10.seconds, backgroundScope)
            val namedInput = Accumulator(context, 10.seconds, backgroundScope)
            defaultInput.bind(source.asMeta())
            namedInput.bind(source.asMeta(), "value")
            defaultInput.awaitState(10.0, t0)
            namedInput.awaitState(10.0, t0)

            source.emit(5.0, t0 + 1.seconds)
            defaultInput.awaitState(15.0, t0 + 1.seconds)
            namedInput.awaitState(15.0, t0 + 1.seconds)
        }
    }

    @Test
    fun testAccumulatorRejectsUnknownInput() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorUnknownInput") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(10.0, t0)).asMeta()
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            assertFailsWith<IllegalStateException> {
                accumulator.bind(source, "other")
            }

            accumulator.bind(source)
            accumulator.awaitState(10.0, t0)
        }
    }

    @Test
    fun testAccumulatorRejectsRepeatedBinding() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorRepeatedBinding") { context ->
            val t0 = Instant.fromEpochMilliseconds(1000)
            val source = CustomTimedState(ValueWithTime(10.0, t0)).asMeta()
            val accumulator = Accumulator(context, 10.seconds, backgroundScope)
            accumulator.bind(source)
            accumulator.awaitState(10.0, t0)

            val exception = assertFailsWith<IllegalStateException> {
                accumulator.bind(ValueState<Double?>(20.0).asMeta(), "value")
            }
            assertEquals("The state is already bound", exception.message)
            assertEquals(ValueWithTime(10.0, t0), accumulator.state.valueWithTime)
        }
    }

    @Test
    fun testAccumulatorFromPlugin() = runTest(timeout = 5.seconds) {
        withTestContext("accumulatorPlugin", { plugin(ControlsUtilitiesPlugin) }) { context ->
            val plugin = context.request(ControlsUtilitiesPlugin)
            val factories = plugin.content(DeviceManager.DEVICE_FACTORY_TARGET)
            assertEquals(Accumulator, factories[Name.of("accumulator")])
        }
    }
}
