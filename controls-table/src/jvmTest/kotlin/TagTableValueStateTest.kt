package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.registerProperty
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.tagtable.timeseries.collectTable
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import space.kscience.tables.toColumnTable
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TagTableValueStateTest {
    private val configuration = TagTableConfiguration(
        sources = emptyMap(),
        timers = mapOf("scan" to FixedRateTimer(1.seconds)),
        tags = mapOf(
            "sensor" to TagTableColumn.controls(
                timer = "scan",
                deviceName = Name.of("source"),
                propertyName = "reading",
            )
        ),
    )

    private class MessageTable(table: TagTable, var cached: ValueWithTime<Meta>) : TagTable by table {
        override val messageFlow = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 2)

        /** Runs before the cached sample is read, as a table that already stored a new sample would behave. */
        var beforeRead: (() -> Unit)? = null

        /** Runs after the cached sample is read, as a table that stores a sample later would behave. */
        var onRead: (() -> Unit)? = null

        override fun readTagWithTime(tag: String): ValueWithTime<Meta> {
            beforeRead?.also { beforeRead = null }?.invoke()
            val value = cached
            onRead?.also { onRead = null }?.invoke()
            return value
        }

        override suspend fun readTag(tag: String): Meta = readTagWithTime(tag).value
        override fun readAllValues(): Map<String, Meta> = mapOf("sensor" to readTagWithTime("sensor").value)
    }

    private fun messageTable(context: Context, initial: ValueWithTime<Meta>) =
        MessageTable(PlcTagTable(context, configuration), initial)

    private fun registerSourceDevice(context: Context) {
        val source = DeviceConstructor(context)
        source.registerProperty("reading", MetaConverter.meta, ValueState(Meta(25)))
        context.request(DeviceManager).registerDevice("source", source)
    }

    @Test
    fun testReadAllValuesIncludesTagQuality() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-table-values-with-quality")
        try {
            val value = Meta(25)
            val quality = Meta { set(TagState.quality, TagState.GOOD_QUALITY) }
            val table = object : TagTable by MessageTable(
                PlcTagTable(context, configuration),
                ValueWithTime(value, Instant.DISTANT_PAST),
            ) {
                override suspend fun readTagQuality(tag: String): TagState {
                    assertEquals("sensor", tag)
                    return TagState(quality)
                }
            }

            assertEquals(
                mapOf("sensor" to value, "sensor.@quality" to quality),
                table.readAllValuesWithTagState(),
            )
        } finally {
            context.close()
        }
    }

    @Test
    fun testTimeSeriesQualityHeaders() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-table-quality-headers")
        try {
            val table = PlcTagTable(context, configuration)
            assertTimeSeriesHeaders(table, Meta.EMPTY)
        } finally {
            context.close()
        }
    }

    @Test
    fun testTimeSeriesIncludeOldValues() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-table-include-old-values") {
            plugin(DeviceManager)
            plugin(ClockManager)
        }
        try {
            registerSourceDevice(context)

            val table = PlcTagTable(context, configuration)

            // When includeOldValues is true, old values (such as initial DISTANT_PAST) are included
            val rowsWithOld = table.readTimeSeries(1.seconds, includeOldValues = true).collectTable(1)
            assertEquals(Meta.EMPTY, rowsWithOld.toColumnTable().getOrNull(0, "sensor"))

            // When includeOldValues is false, old values are skipped
            val rowsWithoutOld = table.readTimeSeries(1.seconds, includeOldValues = false).collectTable(1)
            assertNull(rowsWithoutOld.toColumnTable().getOrNull(0, "sensor"))

            // After the table starts and receives a fresh sample, readTimeSeries with includeOldValues = false includes it
            val message = async {
                table.messageFlow.filterIsInstance<PropertyChangedMessage>().first { it.property == "sensor" }
            }
            try {
                table.start()
                message.await()
            } finally {
                table.stop()
            }

            val rowsWithFresh = table.readTimeSeries(1.seconds, includeOldValues = false).collectTable(1)
            assertEquals(Meta(25), rowsWithFresh.toColumnTable().getOrNull(0, "sensor"))
        } finally {
            context.close()
        }
    }

    @Test
    fun testCreatingStatesDoesNotSubscribe() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-subscriptions")
        try {
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)
            val table = messageTable(context, initial)
            val first = TagTableValueState(table, "sensor")
            val second = TagTableValueState(table, "sensor")

            assertEquals(initial, first.valueWithTime)
            assertEquals(initial, second.valueWithTime)
            runCurrent()
            assertEquals(0, table.messageFlow.subscriptionCount.value)
        } finally {
            context.close()
        }
    }

    @Test
    fun testCurrentValueReadsTableWithoutSubscription() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-current")
        try {
            val table = messageTable(context, ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST))
            val state = TagTableValueState(table, "sensor")
            assertEquals(table.cached, state.valueWithTime)

            val current = ValueWithTime(Meta(25), Instant.fromEpochSeconds(1_000))
            table.cached = current
            repeat(3) { assertEquals(current, state.valueWithTime) }
            assertEquals(current, state.subscribeWithTime().first())
            runCurrent()
            assertEquals(0, table.messageFlow.subscriptionCount.value)
        } finally {
            context.close()
        }
    }

    @Test
    fun testMessagesSupplyValueAndTimeTogether() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-messages")
        try {
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)
            val table = messageTable(context, initial)
            val state = TagTableValueState(table, "sensor")
            val samples = async(start = CoroutineStart.UNDISPATCHED) {
                state.subscribeWithTime().take(2).toList()
            }
            table.messageFlow.subscriptionCount.first { it > 0 }

            val time = Instant.fromEpochSeconds(1_000)
            table.cached = ValueWithTime(Meta(99), time + 1.seconds)
            table.messageFlow.emit(PropertyChangedMessage(time, "other", Meta(100)))
            runCurrent()
            table.messageFlow.emit(PropertyChangedMessage(time, "sensor", Meta(10)))

            assertEquals(listOf(initial, ValueWithTime(Meta(10), time)), samples.await())
            assertEquals(table.cached, state.valueWithTime)
            runCurrent()
            assertEquals(0, table.messageFlow.subscriptionCount.value)
        } finally {
            context.close()
        }
    }

    @Test
    fun testUpdateDuringInitialReadIsNotLost() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-first-update")
        try {
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)
            val table = messageTable(context, initial)
            val state = TagTableValueState(table, "sensor")
            val last = ValueWithTime(Meta(30), Instant.fromEpochSeconds(1_000))
            table.onRead = {
                table.cached = last
                assertTrue(table.messageFlow.tryEmit(PropertyChangedMessage(last.time, "sensor", last.value)))
            }

            assertEquals(listOf(initial, last), state.subscribeWithTime().take(2).toList())
            assertEquals(last, state.valueWithTime)
        } finally {
            context.close()
        }
    }

    @Test
    fun testStoredSampleIsNotRepeatedByItsMessage() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-stored-sample")
        try {
            val table = messageTable(context, ValueWithTime(Meta(25), Instant.DISTANT_PAST))
            val state = TagTableValueState(table, "sensor")
            val stored = ValueWithTime(Meta(30), Instant.fromEpochSeconds(1_000))
            val next = ValueWithTime(Meta(40), stored.time + 1.seconds)
            //the table stores the sample before it emits the message, so the message repeats the initial read
            table.beforeRead = {
                table.cached = stored
                assertTrue(table.messageFlow.tryEmit(PropertyChangedMessage(stored.time, "sensor", stored.value)))
            }
            val samples = async(start = CoroutineStart.UNDISPATCHED) {
                state.subscribeWithTime().take(2).toList()
            }

            table.cached = next
            table.messageFlow.emit(PropertyChangedMessage(next.time, "sensor", next.value))

            assertEquals(listOf(stored, next), samples.await())
        } finally {
            context.close()
        }
    }

    @Test
    fun testBufferedMessagesKeepIncreasingTime() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-buffered-order")
        try {
            val table = messageTable(context, ValueWithTime(Meta(25), Instant.DISTANT_PAST))
            val state = TagTableValueState(table, "sensor")
            val time = Instant.fromEpochSeconds(1_000)
            val stale = ValueWithTime(Meta(30), time)
            val stored = ValueWithTime(Meta(40), time + 1.seconds)
            //both messages are buffered while the current sample is already the later one
            table.beforeRead = {
                table.cached = stored
                assertTrue(table.messageFlow.tryEmit(PropertyChangedMessage(stale.time, "sensor", stale.value)))
                assertTrue(table.messageFlow.tryEmit(PropertyChangedMessage(stored.time, "sensor", stored.value)))
            }
            val samples = async(start = CoroutineStart.UNDISPATCHED) {
                state.subscribeWithTime().take(2).toList()
            }

            val next = ValueWithTime(Meta(50), stored.time + 1.seconds)
            table.cached = next
            table.messageFlow.emit(PropertyChangedMessage(next.time, "sensor", next.value))

            assertEquals(listOf(stored, next), samples.await())
        } finally {
            context.close()
        }
    }

    @Test
    fun testPlcTableRetainsSampleTimeAfterStop() = runTest(timeout = 5.seconds) {
        val context = testContext("tag-state-table") {
            plugin(DeviceManager)
            plugin(ClockManager)
        }

        registerSourceDevice(context)
        val table = PlcTagTable(context, configuration)
        val state = table.subscribe("sensor")
        assertSame(state, table.subscribe("sensor"))
        assertEquals(ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST), state.valueWithTime)
        assertEquals(state.valueWithTime, table.readTagWithTime("sensor"))
        assertFailsWith<IllegalStateException> { table.readTagWithTime("missing") }
        assertFailsWith<IllegalStateException> { TagTableValueState(table, "missing").valueWithTime }
        val message = async {
            table.messageFlow.filterIsInstance<PropertyChangedMessage>().first { it.property == "sensor" }
        }
        val sample = try {
            table.start()
            message.await()
        } finally {
            table.stop()
        }
        val expected = ValueWithTime(sample.value, sample.time)
        assertEquals(expected, table.readTagWithTime("sensor"))
        assertEquals(expected, state.valueWithTime)
        assertEquals(expected, state.subscribeWithTime().first())
        assertEquals(Meta(25), table.readTag("sensor"))
        assertEquals(mapOf("sensor" to Meta(25)), table.readAllValues())
        assertSame(state, table.subscribe("sensor"))

        context.close()

    }
}
