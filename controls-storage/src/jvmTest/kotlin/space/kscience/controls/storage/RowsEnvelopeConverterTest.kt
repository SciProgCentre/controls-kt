package space.kscience.controls.storage

import kotlinx.serialization.SerializationException
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.io.*
import space.kscience.dataforge.meta.*
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RowsEnvelopeConverterTest {

    @Test
    fun testInflatedByteLimit() {
        val table = RowTable(
            listOf(SimpleColumnHeader<Meta>("value", typeOf<Meta>(), Meta.EMPTY)),
            listOf(MapRow(mapOf("value" to Meta("Температура 🌡")))),
        )
        val converter = ZipRowsEnvelopeConverter.meta
        val envelope = converter.writeRows(table)
        val size = InflaterInputStream(ByteArrayInputStream(envelope.data!!.toByteArray())).use { it.readBytes().size }
        assertTrue(size > "Температура 🌡".length)
        val error = assertFailsWith<IOException> { converter.readRows(envelope, size - 1L) }
        assertTrue(error.message.orEmpty().contains("maxInflatedBytes"))
        for (limit in listOf(size.toLong(), size + 1L, Long.MAX_VALUE)) {
            assertEquals(Meta("Температура 🌡"), converter.readRows(envelope, limit).rowSequence().single().getOrNull("value"))
        }
        assertEquals(Meta("Температура 🌡"), converter.readRows(envelope).rowSequence().single().getOrNull("value"))
        assertFailsWith<IllegalArgumentException> { converter.readRows(envelope, 0) }
        assertFailsWith<IllegalArgumentException> { converter.readRows(envelope, -1) }
    }

    private fun compressedEnvelope(body: String): Envelope {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(body.encodeToByteArray()) }
        return Envelope(
            Meta { Envelope.ENVELOPE_DATA_TYPE_KEY put ZipRowsEnvelopeConverter.ENVELOPE_TYPE },
            output.toByteArray().asBinary(),
        )
    }

    @Test
    fun testTrailingBytesCountTowardsLimit() {
        val body = "[]" + " ".repeat(10_000)
        val envelope = compressedEnvelope(body)
        assertTrue(envelope.data!!.size < 100)
        assertFailsWith<IOException> { ZipRowsEnvelopeConverter.meta.readRows(envelope, 100) }
        assertEquals(0, ZipRowsEnvelopeConverter.meta.readRows(envelope, body.length.toLong()).rowSequence().count())
        assertFailsWith<SerializationException> {
            ZipRowsEnvelopeConverter.meta.readRows(compressedEnvelope("[] false"), 100)
        }
        assertFailsWith<SerializationException> {
            ZipRowsEnvelopeConverter.meta.readRows(compressedEnvelope("[{"), 100)
        }
    }

    @Test
    fun testInvalidEnvelopeStillFails() {
        val envelope = compressedEnvelope("[]")
        assertFailsWith<IllegalArgumentException> {
            ZipRowsEnvelopeConverter.meta.readRows(Envelope(Meta.EMPTY, envelope.data), 100)
        }
        assertFailsWith<IllegalStateException> {
            ZipRowsEnvelopeConverter.meta.readRows(Envelope(envelope.meta, null), 100)
        }
        val bytes = envelope.data!!.toByteArray()
        assertFailsWith<IOException> {
            ZipRowsEnvelopeConverter.meta.readRows(Envelope(envelope.meta, bytes.copyOf(bytes.size - 2).asBinary()), 100)
        }
    }

    @Test
    fun testLimitedReadsAndSkip() {
        val source = object : ByteArrayInputStream(ByteArray(100)) {
            var closed = false
            fun bytesRead(): Int = pos
            override fun close() { closed = true }
        }
        InflatedByteLimitInputStream(source, 5).use { input ->
            assertEquals(0, input.read())
            assertEquals(2, input.read(ByteArray(2)))
            assertEquals(2L, input.skip(2))
            assertEquals(0, input.read(ByteArray(0)))
            assertFailsWith<IOException> { input.skip(1) }
            assertEquals(6, source.bytesRead())
        }
        assertTrue(source.closed)
        InflatedByteLimitInputStream(ByteArrayInputStream(ByteArray(5)), 5).use { input ->
            assertEquals(5, input.read(ByteArray(10)))
            assertEquals(-1, input.read())
            assertEquals(-1, input.read(ByteArray(1)))
            assertEquals(0, input.read(ByteArray(0)))
        }
    }

    @Test
    fun testLimitFailureClosesTheSource() {
        val source = object : ByteArrayInputStream(ByteArray(1_000_000)) {
            var closed = false
            fun bytesRead(): Int = pos
            override fun close() { closed = true }
        }
        assertFailsWith<IOException> {
            InflatedByteLimitInputStream(source, 100).use { it.readBytes() }
        }
        assertEquals(101, source.bytesRead())
        assertTrue(source.closed)
    }

    @Test
    fun testSerialization() {
        val columnsCount = 10
        val rowsCount = 10000

        val headers = (1..columnsCount).map { i ->
            SimpleColumnHeader<Double>("column_$i", typeOf<Double>(), Meta.EMPTY)
        }

        val random = Random(42)
        val data = List(rowsCount) {
            MapRow(headers.associate { header ->
                header.name to random.nextDouble()
            })
        }

        val table = RowTable(headers, data)

        val converter = ZipRowsEnvelopeConverter(MetaConverter.double, typeOf<Double>())

        val envelope = converter.writeRows(table)

        val envelopeFormat = TaggedEnvelopeFormat(Global.io)

        val binary = Binary {
            envelopeFormat.writeTo(this, envelope)
        }
        val bytes = binary.toByteArray()
        println(bytes.size)

        val readEnvelope = envelopeFormat.readFrom(bytes.asBinary())


        val resultTable = converter.readRows(readEnvelope)

        assertEquals(table.headers.size, resultTable.headers.size)
        table.headers.zip(resultTable.headers).forEach { (expected, actual) ->
            assertEquals(expected.name, actual.name)
        }

        val resultRows = resultTable.rowSequence().toList()
        assertEquals(rowsCount, resultRows.size)

        for (i in 0 until rowsCount) {
            val expectedRow = data[i]
            val actualRow = resultRows[i]
            headers.forEach { header ->
                assertEquals(
                    expectedRow.getOrNull(header.name),
                    actualRow.getOrNull(header.name),
                    "Error in row $i, column ${header.name}"
                )
            }
        }
    }
}
