package space.kscience.controls.storage

import kotlinx.serialization.SerializationException
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
import kotlin.reflect.typeOf
import kotlin.test.*

class ReadRowsWithInflatedByteLimitTest {

    @Test
    fun testInflatedByteLimit() {
        val header = SimpleColumnHeader<Meta>("value", typeOf<Meta>(), Meta { "unit" put "°C" })
        val value = Meta("Температура 🌡")
        val table = RowTable(listOf(header), listOf(MapRow(mapOf("value" to value))))
        val converter = ZipRowsEnvelopeConverter.meta
        val envelope = converter.writeRows(table)
        val size = InflaterInputStream(ByteArrayInputStream(envelope.data!!.toByteArray())).use { it.readBytes().size }
        assertTrue(size > "Температура 🌡".length)
        val limit = size - 1L
        val error = assertFailsWith<InflatedByteLimitExceededException> {
            converter.readRowsWithInflatedByteLimit(envelope, limit)
        }
        assertEquals(limit, error.maxInflatedBytes)
        val results = listOf(converter.readRows(envelope)) + listOf(size.toLong(), size + 1L, Long.MAX_VALUE).map {
            converter.readRowsWithInflatedByteLimit(envelope, it)
        }
        results.forEach { result ->
            val actualHeader = result.headers.single()
            assertEquals(header.name, actualHeader.name)
            assertEquals(header.type, actualHeader.type)
            assertEquals(header.meta, actualHeader.meta)
            assertEquals(value, result.rowSequence().single().getOrNull("value"))
        }
        assertFailsWith<IllegalArgumentException> { converter.readRowsWithInflatedByteLimit(envelope, 0) }
        assertFailsWith<IllegalArgumentException> { converter.readRowsWithInflatedByteLimit(envelope, -1) }
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
        assertFailsWith<InflatedByteLimitExceededException> {
            ZipRowsEnvelopeConverter.meta.readRowsWithInflatedByteLimit(envelope, 100)
        }
        assertEquals(
            0,
            ZipRowsEnvelopeConverter.meta.readRowsWithInflatedByteLimit(envelope, body.length.toLong()).rowSequence().count(),
        )
    }

    @Test
    fun testInvalidEnvelopeStillFails() {
        val converter = ZipRowsEnvelopeConverter.meta
        for (body in listOf("[] false", "[{")) {
            val envelope = compressedEnvelope(body)
            assertFailsWith<SerializationException> { converter.readRows(envelope) }
            assertFailsWith<SerializationException> { converter.readRowsWithInflatedByteLimit(envelope, 100) }
        }
        val envelope = compressedEnvelope("[]")
        val wrongType = Envelope(Meta.EMPTY, envelope.data)
        val missingData = Envelope(envelope.meta, null)
        assertFailsWith<IllegalArgumentException> { converter.readRows(wrongType) }
        assertFailsWith<IllegalArgumentException> { converter.readRowsWithInflatedByteLimit(wrongType, 100) }
        assertFailsWith<IllegalStateException> { converter.readRows(missingData) }
        assertFailsWith<IllegalStateException> { converter.readRowsWithInflatedByteLimit(missingData, 100) }
        val bytes = envelope.data!!.toByteArray()
        val truncated = Envelope(envelope.meta, bytes.copyOf(bytes.size - 2).asBinary())
        assertFailsWith<IOException> { converter.readRows(truncated) }
        val error = assertFailsWith<IOException> { converter.readRowsWithInflatedByteLimit(truncated, 100) }
        assertFalse(error is InflatedByteLimitExceededException)
    }

    @Test
    fun testCellConversionFailure() {
        val failure = IllegalArgumentException("Invalid cell")
        val cellConverter = object : MetaConverter<Meta> by MetaConverter.meta {
            override fun read(source: Meta): Meta = throw failure
        }
        val converter = ZipRowsEnvelopeConverter(cellConverter, typeOf<Meta>())
        val envelope = compressedEnvelope("[{\"value\":42}]")
        assertSame(failure, assertFailsWith<IllegalArgumentException> { converter.readRows(envelope) })
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            converter.readRowsWithInflatedByteLimit(envelope, 100)
        })
    }

    @Test
    fun testDecoderBorrowsItsInput() {
        for (body in listOf("[]", "[{")) {
            val input = object : ByteArrayInputStream(body.encodeToByteArray()) {
                var closed = false
                override fun close() { closed = true }
            }
            input.use {
                if (body == "[]") {
                    assertEquals(0, ZipRowsEnvelopeConverter.meta.readInflatedRows(Meta.EMPTY, it).rowSequence().count())
                } else {
                    assertFailsWith<SerializationException> { ZipRowsEnvelopeConverter.meta.readInflatedRows(Meta.EMPTY, it) }
                }
                assertFalse(input.closed)
            }
            assertTrue(input.closed)
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
            assertFailsWith<InflatedByteLimitExceededException> { input.skip(1) }
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
        assertFailsWith<InflatedByteLimitExceededException> {
            InflatedByteLimitInputStream(source, 100).use { it.readBytes() }
        }
        assertEquals(101, source.bytesRead())
        assertTrue(source.closed)
    }
}
