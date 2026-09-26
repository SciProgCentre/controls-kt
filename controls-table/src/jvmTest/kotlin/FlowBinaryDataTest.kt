package space.kscience.controls.tagtable

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.flowBinaryData
import space.kscience.controls.tagtable.timeseries.TimeSeriesRows
import space.kscience.controls.tagtable.timeseries.TimeSeriesValues
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.tables.SimpleColumnHeader
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FlowBinaryDataTest {
    private val start = Instant.fromEpochSeconds(1_000)
    private val converter = ZipRowsEnvelopeConverter(MetaConverter.meta, typeOf<Meta>())
    private val headers = listOf(
        TagTable.timeColumnHeader,
        SimpleColumnHeader("value", typeOf<Meta>(), Meta { "unit" put "test" }),
    )

    private fun table(context: Context, samples: List<TimeSeriesValues<Meta>>): TagTable =
        object : TagTable by PlcTagTable(context, TagTableConfiguration(
            sources = emptyMap(),
            timers = emptyMap(),
            properties = mapOf("value" to InternalTagTableColumn(
                timer = "unused", deviceName = Name.of("fixture"), propertyName = "value",
            )),
        )) {
            override val clock: Clock = object : Clock {
                override fun now(): Instant = start
            }

            override fun readAllValues(): Map<String, Meta> = mapOf("value" to Meta(999))

            override fun readTimeSeries(interval: Duration, withTagState: Boolean): TimeSeriesRows<Meta> = object : TimeSeriesRows<Meta> {
                override val headers = this@FlowBinaryDataTest.headers
                override fun subscribe() = samples.asFlow()
            }
        }

    private fun values(envelope: Envelope): List<Meta?> =
        converter.readRows(envelope).rowSequence().map { it.getOrNull("value") }.toList()

    @Test
    fun testDisabledCompressionDoesNotAddAnchors() = runTest {
        val context = Context("disabled-compression") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val samples = List(4) { ValueWithTime(mapOf("value" to Meta(it)), start + it.seconds) }
            for (compression in listOf(null, RowsCompression(false, false))) {
                val envelopes = table(context, samples).flowBinaryData(
                    1.seconds, converter, maxRows = 2, compression = compression,
                ).toList()

                assertEquals(2, envelopes.size)
                assertEquals(listOf(listOf(Meta(0), Meta(1)), listOf(Meta(2), Meta(3))), envelopes.map(::values))
                envelopes.forEachIndexed { index, envelope ->
                    val rows = converter.readRows(envelope)
                    assertEquals(headers.map { it.name }, rows.headers.map { it.name })
                    assertEquals(headers.map { it.meta }, rows.headers.map { it.meta })
                    assertEquals(2, envelope.meta["numberOfRows"].int)
                    assertEquals(start + (index * 2).seconds, envelope.meta[RowEnvelopeMetaSpec.startTime])
                    assertEquals(start + (index * 2 + 1).seconds, envelope.meta[RowEnvelopeMetaSpec.endTime])
                    assertEquals(
                        samples.drop(index * 2).take(2).map { Meta(it.time.toString()) },
                        rows.rowSequence().map { it.getOrNull("@time") }.toList(),
                    )
                }
            }
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testCompressedBlocksKeepTheirAnchor() = runTest {
        val context = Context("active-compression") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val samples = List(3) { ValueWithTime(mapOf("value" to Meta(it)), start + it.seconds) }
            val envelopes = table(context, samples).flowBinaryData(
                1.seconds, converter, maxRows = 2,
                compression = RowsCompression(skipUnchangedRows = false, skipUnchangedValues = true),
            ).toList()

            assertEquals(listOf(listOf(Meta(0), Meta(1)), listOf(Meta(999), Meta(2))), envelopes.map(::values))
        } finally {
            context.cancel()
            context.close()
        }
    }
}
