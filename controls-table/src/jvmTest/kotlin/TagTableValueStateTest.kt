package space.kscience.controls.tagtable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.constructor.DeviceConstructor
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.registerProperty
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
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

    private class MessageTable(table: TagTable, var cached: Meta) : TagTable by table {
        override val messageFlow = MutableSharedFlow<DeviceMessage>()
        override fun readAll(): Map<String, Meta> = mapOf("sensor" to cached)
    }

    @Test
    fun testCurrentValueKeepsUnknownTime() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-initial") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val table = MessageTable(PlcTagTable(context, configuration), Meta(25))
            val state = TagTableValueState(table, "sensor")
            val initial = ValueWithTime(Meta(25), Instant.DISTANT_PAST)

            assertEquals(initial, state.valueWithTime)
            assertEquals(initial, state.subscribeWithTime().first())
            assertEquals(
                ValueWithTime(Meta.EMPTY, Instant.DISTANT_PAST),
                TagTableValueState(table, "missing").valueWithTime,
            )
            table.cached = Meta(99)
            repeat(3) { assertEquals(initial, state.valueWithTime) }
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testMessagesSupplyValueAndTimeTogether() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-messages") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val table = MessageTable(PlcTagTable(context, configuration), Meta(25))
            val state = TagTableValueState(table, "sensor")
            table.messageFlow.subscriptionCount.first { it == 1 }
            val time = Instant.fromEpochSeconds(1_000)
            table.cached = Meta(99)
            table.messageFlow.emit(PropertyChangedMessage(time, "sensor", Meta(10)))
            val first = ValueWithTime(Meta(10), time)
            assertEquals(first, state.subscribeWithTime().first { it.time == time })
            assertEquals(first, state.valueWithTime)
            assertEquals(first, state.subscribeWithTime().first())

            table.messageFlow.emit(PropertyChangedMessage(time + 1.seconds, "other", Meta(100)))
            runCurrent()
            assertEquals(first, state.valueWithTime)

            repeat(10) { index ->
                table.messageFlow.emit(PropertyChangedMessage(time + (index + 2).seconds, "sensor", Meta(index)))
            }
            val latest = ValueWithTime(Meta(9), time + 11.seconds)
            assertEquals(latest, state.subscribeWithTime().first { it.time == latest.time })
            assertEquals(latest, state.valueWithTime)
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testCacheAndCollectorSurviveStopStart() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-restart") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(DeviceManager)
        }
        try {
            val source = DeviceConstructor(context)
            source.registerProperty("reading", MetaConverter.meta, ValueState(Meta(25)))
            context.request(DeviceManager).registerDevice("source", source)
            val table = PlcTagTable(context, configuration)
            val cachedState = table.valueState("sensor")
            assertSame(cachedState, table.valueState("sensor"))
            val messages = MessageTable(table, Meta(25))
            val state = TagTableValueState(messages, "sensor")
            messages.messageFlow.subscriptionCount.first { it == 1 }

            table.start()
            table.stop()
            table.start()
            assertSame(cachedState, table.valueState("sensor"))
            assertEquals(1, messages.messageFlow.subscriptionCount.value)
            val time = Instant.fromEpochSeconds(1_000)
            messages.messageFlow.emit(PropertyChangedMessage(time, "sensor", Meta(30)))
            assertEquals(ValueWithTime(Meta(30), time), state.subscribeWithTime().first { it.time == time })
            table.stop()
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testOwnerCancellationStopsUpstreamCollector() = runTest(timeout = 5.seconds) {
        val context = Context("tag-state-cancel") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val table = MessageTable(PlcTagTable(context, configuration), Meta(25))
            val state = TagTableValueState(table, "sensor")
            table.messageFlow.subscriptionCount.first { it == 1 }

            table.cancel()
            table.messageFlow.subscriptionCount.first { it == 0 }
            table.messageFlow.emit(PropertyChangedMessage(Instant.fromEpochSeconds(1_000), "sensor", Meta(30)))
            assertEquals(ValueWithTime(Meta(25), Instant.DISTANT_PAST), state.valueWithTime)
        } finally {
            context.cancel()
            context.close()
        }
    }
}
