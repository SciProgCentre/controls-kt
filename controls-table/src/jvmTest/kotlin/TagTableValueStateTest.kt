package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.registerProperty
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TagTableValueStateTest {
    private val configuration = TagTableConfiguration(
        sources = emptyMap(),
        timers = mapOf("scan" to FixedRateTimer(1.seconds)),
        properties = mapOf("sensor" to InternalTagTableColumn(
            timer = "scan",
            deviceName = Name.of("source"),
            propertyName = "reading",
        )),
    )

    private class MessageTable(table: TagTable, var cached: ValueWithTime<Meta>) : TagTable by table {
        override val messageFlow = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 2)

        /** Runs before the cached sample is read, as a table that already stored a new sample would behave. */
        var beforeRead: (() -> Unit)? = null

        /** Runs after the cached sample is read, as a table that stores a sample later would behave. */
        var onRead: (() -> Unit)? = null

        override fun readWithTime(tag: String): ValueWithTime<Meta> {
            beforeRead?.also { beforeRead = null }?.invoke()
            val value = cached
            onRead?.also { onRead = null }?.invoke()
            return value
        }

        override suspend fun read(tag: String): Meta = readWithTime(tag).value
        override fun readAll(): Map<String, Meta> = mapOf("sensor" to readWithTime("sensor").value)
    }

    @Test
    fun testCreatingStatesDoesNotSubscribe() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-subscriptions") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)
            val table = MessageTable(PlcTagTable(context, configuration), initial)
            val first = TagTableValueState(table, "sensor")
            val second = TagTableValueState(table, "sensor")

            assertEquals(initial, first.valueWithTime)
            assertEquals(initial, second.valueWithTime)
            runCurrent()
            assertEquals(0, table.messageFlow.subscriptionCount.value)
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testCurrentValueReadsTableWithoutSubscription() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-current") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val table = MessageTable(
                PlcTagTable(context, configuration),
                ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST),
            )
            val state = TagTableValueState(table, "sensor")
            assertEquals(table.cached, state.valueWithTime)

            val current = ValueWithTime(Meta(25), Instant.fromEpochSeconds(1_000))
            table.cached = current
            repeat(3) { assertEquals(current, state.valueWithTime) }
            assertEquals(current, state.subscribeWithTime().first())
            runCurrent()
            assertEquals(0, table.messageFlow.subscriptionCount.value)
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testMessagesSupplyValueAndTimeTogether() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-messages") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)
            val table = MessageTable(PlcTagTable(context, configuration), initial)
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
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testUpdateDuringInitialReadIsNotLost() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-first-update") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)
            val table = MessageTable(PlcTagTable(context, configuration), initial)
            val state = TagTableValueState(table, "sensor")
            val last = ValueWithTime(Meta(30), Instant.fromEpochSeconds(1_000))
            table.onRead = {
                table.cached = last
                assertTrue(table.messageFlow.tryEmit(PropertyChangedMessage(last.time, "sensor", last.value)))
            }

            assertEquals(listOf(initial, last), state.subscribeWithTime().take(2).toList())
            assertEquals(last, state.valueWithTime)
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testStoredSampleIsNotRepeatedByItsMessage() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-stored-sample") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val table = MessageTable(
                PlcTagTable(context, configuration),
                ValueWithTime(Meta(25), Instant.DISTANT_PAST),
            )
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
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testBufferedMessagesKeepIncreasingTime() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-buffered-order") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val table = MessageTable(
                PlcTagTable(context, configuration),
                ValueWithTime(Meta(25), Instant.DISTANT_PAST),
            )
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
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testPlcTableRetainsSampleTimeAfterStop() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-table") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(DeviceManager)
            plugin(ClockManager)
        }
        try {
            val source = DeviceConstructor(context)
            source.registerProperty("reading", MetaConverter.meta, ValueState(Meta(25)))
            context.request(DeviceManager).registerDevice("source", source)
            val table = PlcTagTable(context, configuration)
            val state = table.valueState("sensor")
            assertSame(state, table.valueState("sensor"))
            assertEquals(ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST), state.valueWithTime)
            assertEquals(state.valueWithTime, table.readWithTime("sensor"))
            assertFailsWith<IllegalStateException> { table.readWithTime("missing") }
            assertFailsWith<IllegalStateException> { TagTableValueState(table, "missing").valueWithTime }
            val message = async(start = CoroutineStart.UNDISPATCHED) {
                table.messageFlow.filterIsInstance<PropertyChangedMessage>().first { it.property == "sensor" }
            }
            val row = async(start = CoroutineStart.UNDISPATCHED) {
                table.messageFlow.filterIsInstance<PropertyChangedMessage>()
                    .first { it.property == TagTable.ROW_PROPERTY_NAME }
            }

            val sample = try {
                table.start()
                message.await().also {
                    assertEquals(Meta { set("sensor", Meta(25)) }, row.await().value)
                }
            } finally {
                table.stop()
            }
            val expected = ValueWithTime(sample.value, sample.time)
            assertEquals(expected, table.readWithTime("sensor"))
            assertEquals(expected, state.valueWithTime)
            assertEquals(expected, state.subscribeWithTime().first())
            assertEquals(Meta(25), table.read("sensor"))
            assertEquals(mapOf("sensor" to Meta(25)), table.readAll())
            assertSame(state, table.valueState("sensor"))
        } finally {
            context.cancel()
            context.close()
        }
    }
}
