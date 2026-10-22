package space.kscience.controls.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import space.kscience.dataforge.io.*
import space.kscience.dataforge.meta.*
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DeflaterOutputStream
import kotlin.reflect.typeOf
import kotlin.test.*

class ReadRowsToTest {
    private val converter = ZipRowsEnvelopeConverter.meta

    private fun compressedEnvelope(body: String): Envelope {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(body.encodeToByteArray()) }
        return Envelope(
            Meta { Envelope.ENVELOPE_DATA_TYPE_KEY put ZipRowsEnvelopeConverter.ENVELOPE_TYPE },
            output.toByteArray().asBinary(),
        )
    }

    @Test
    fun testInflatedByteLimitIsInclusive(): Unit = runBlocking {
        val body = "[{\"value\":\"Температура 🌡\"}]"
        val envelope = compressedEnvelope(body)
        val size = body.encodeToByteArray().size.toLong()
        val error = assertFailsWith<InflatedByteLimitExceededException> {
            converter.readRowsTo(envelope, maxInflatedBytes = size - 1) {}
        }
        assertEquals(size - 1, error.maxInflatedBytes)
        for (limit in listOf(size, size + 1, Long.MAX_VALUE)) {
            assertEquals(size, converter.readRowsTo(envelope, maxInflatedBytes = limit) {})
        }
        for (limit in listOf(0L, -1L)) {
            assertFailsWith<IllegalArgumentException> {
                converter.readRowsTo(envelope, maxInflatedBytes = limit) {}
            }
        }
    }

    @Test
    fun testHeaderPrecedesRows(): Unit = runBlocking {
        val header = SimpleColumnHeader<Meta>("value", typeOf<Meta>(), Meta { "unit" put "°C" })
        val value = Meta("Температура 🌡")
        val envelope = converter.writeRows(RowTable(listOf(header), listOf(MapRow(mapOf("value" to value)))))
        val events = mutableListOf<String>()

        converter.readRowsTo(envelope, maxInflatedBytes = 100, onHeader = {
            events += "header"
            val actual = it.single()
            assertEquals(header.name, actual.name)
            assertEquals(header.type, actual.type)
            assertEquals(header.meta, actual.meta)
        }) {
            events += "row"
            assertEquals(value, it.getOrNull("value"))
        }

        assertEquals(listOf("header", "row"), events)
    }

    @Test
    fun testHeaderIsDeliveredForEmptyRows(): Unit = runBlocking {
        val header = SimpleColumnHeader<Meta>("empty", typeOf<Meta>(), Meta { "unit" put "s" })
        val envelope = converter.writeRows(RowTable<Meta>(listOf(header), emptyList()))
        var headerCalls = 0
        assertEquals(2L, converter.readRowsTo(envelope, maxInflatedBytes = 2, onHeader = {
            headerCalls++
            assertEquals(header.name, it.single().name)
            assertEquals(header.meta, it.single().meta)
        }) { fail("Empty array has no rows") })
        assertEquals(1, headerCalls)
    }

    @Test
    fun testTrailingWhitespaceCountsTowardLimit(): Unit = runBlocking {
        val body = " \n[]" + " \t\r\n".repeat(4_000)
        val withWhitespace = compressedEnvelope(body)
        assertFailsWith<InflatedByteLimitExceededException> {
            converter.readRowsTo(withWhitespace, maxInflatedBytes = 100) {}
        }
        assertEquals(body.encodeToByteArray().size.toLong(), converter.readRowsTo(
            withWhitespace, maxInflatedBytes = body.encodeToByteArray().size.toLong(),
        ) { fail("Empty array has no rows") })
    }

    @Test
    fun testHeaderFallbackName(): Unit = runBlocking {
        val envelope = Envelope(Meta {
            Envelope.ENVELOPE_DATA_TYPE_KEY put ZipRowsEnvelopeConverter.ENVELOPE_TYPE
            "@header.column[0].meta.unit" put "m"
        }, compressedEnvelope("[]").data)
        val expected = converter.readRows(envelope).headers.single()
        assertEquals("default", expected.name)
        var headerCalls = 0
        converter.readRowsTo(envelope, maxInflatedBytes = 2, onHeader = {
            headerCalls++
            val actual = it.single()
            assertEquals(expected.name, actual.name)
            assertEquals(expected.type, actual.type)
            assertEquals(expected.meta, actual.meta)
        }) { fail("Empty array has no rows") }
        assertEquals(1, headerCalls)
    }

    @Test
    fun testWrittenOrderMatchesOrdinaryDecoder(): Unit = runBlocking {
        val body = "[{\"value\":3},{\"value\":1},{\"value\":2}]"
        val envelope = compressedEnvelope(body)
        val actual = mutableListOf<Meta?>()
        assertEquals(body.length.toLong(), converter.readRowsTo(envelope, maxInflatedBytes = 100) {
            actual += it.getOrNull("value")
        })
        assertEquals(listOf<Meta?>(Meta(3), Meta(1), Meta(2)), actual)
        val ordinary = converter.readRows(envelope)
        repeat(2) { assertEquals(actual, ordinary.rowSequence().map { it.getOrNull("value") }.toList()) }
    }

    @Test
    fun testEnvelopeCanBeReadAfterLimitFailure(): Unit = runBlocking {
        val envelope = compressedEnvelope("[{\"value\":3},{\"value\":1},{\"value\":2}]")
        assertFailsWith<InflatedByteLimitExceededException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 1) {}
        }
        assertEquals(3, converter.readRows(envelope).rowSequence().count())
    }

    @Test
    fun testReturnsDecodedByteCountForEachEnvelope(): Unit = runBlocking {
        for (body in listOf("[{\"value\":1}]", "[{\"value\":\"Температура\"},{\"value\":3}]")) {
            val count = converter.readRowsTo(compressedEnvelope(body), maxInflatedBytes = 100) {}
            assertEquals(body.encodeToByteArray().size.toLong(), count)
        }
    }

    @Test
    fun testMalformedTailFailsAfterDeliveredRows(): Unit = runBlocking {
        val delivered = mutableListOf<Meta?>()
        val envelope = compressedEnvelope("[{\"value\":1},{\"value\":2}] false")

        assertFailsWith<SerializationException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 100) { delivered += it.getOrNull("value") }
        }

        assertEquals(listOf<Meta?>(Meta(1), Meta(2)), delivered)
    }

    @Test
    fun testMalformedJsonIsRejectedByBothDecoders(): Unit = runBlocking {
        for (body in listOf("[] false", "[] []", "[{", "[{},]")) {
            val envelope = compressedEnvelope(body)
            assertFailsWith<SerializationException> { converter.readRows(envelope) }
            assertFailsWith<SerializationException> {
                converter.readRowsTo(envelope, maxInflatedBytes = 100) {}
            }
        }
    }

    @Test
    fun testTruncatedCompressedDataIsNotReportedAsLimitFailure(): Unit = runBlocking {
        val envelope = compressedEnvelope("[]")
        val bytes = envelope.data!!.toByteArray()
        val truncated = Envelope(envelope.meta, bytes.copyOf(bytes.size - 2).asBinary())
        assertFailsWith<IOException> { converter.readRows(truncated) }
        val error = assertFailsWith<IOException> { converter.readRowsTo(truncated, maxInflatedBytes = 100) {} }
        assertFalse(error is InflatedByteLimitExceededException)
    }

    @Test
    fun testTrailingCompressedBytesMatchOrdinaryDecoder(): Unit = runBlocking {
        val envelope = compressedEnvelope("[]")
        val bytes = envelope.data!!.toByteArray()
        val withTrailingCompressedBytes = Envelope(envelope.meta, (bytes + byteArrayOf(1, 2, 3)).asBinary())
        assertEquals(0, converter.readRows(withTrailingCompressedBytes).rowSequence().count())
        assertEquals(2L, converter.readRowsTo(withTrailingCompressedBytes, maxInflatedBytes = 100) {})
    }

    @Test
    fun testInvalidEnvelope(): Unit = runBlocking {
        val envelope = compressedEnvelope("[]")
        assertFailsWith<IllegalArgumentException> {
            converter.readRowsTo(Envelope(Meta.EMPTY, envelope.data), maxInflatedBytes = 100) {}
        }
        assertFailsWith<IllegalStateException> {
            converter.readRowsTo(Envelope(envelope.meta, null), maxInflatedBytes = 100) {}
        }
    }

    @Test
    fun testCellConversionFailureIsPropagated(): Unit = runBlocking {
        val failure = IllegalArgumentException("Invalid cell")
        val cellConverter = object : MetaConverter<Meta> by MetaConverter.meta {
            override fun read(source: Meta): Meta = throw failure
        }
        val failingConverter = ZipRowsEnvelopeConverter(cellConverter, typeOf<Meta>())
        val envelope = compressedEnvelope("[{\"value\":42}]")
        assertSame(failure, assertFailsWith<IllegalArgumentException> { failingConverter.readRows(envelope) })
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            failingConverter.readRowsTo(envelope, maxInflatedBytes = 100) { fail("Conversion failed") }
        })
    }

    @Test
    fun testHeaderCallbackFailureStopsBeforeRows(): Unit = runBlocking {
        val failure = IllegalArgumentException("Invalid header")
        val envelope = compressedEnvelope("[{\"value\":42}]")
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 100, onHeader = { throw failure }) {
                fail("Header callback failed")
            }
        })
    }

    @Test
    fun testRowCallbackFailureIsPropagated(): Unit = runBlocking {
        val failure = IllegalArgumentException("Invalid row")
        val envelope = compressedEnvelope("[{\"value\":42}]")
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 100) { throw failure }
        })
    }

    @Test
    fun testStreamingConversionFailurePrecedesMalformedTail(): Unit = runBlocking {
        val failure = IllegalArgumentException("Invalid cell")
        val cellConverter = object : MetaConverter<Meta> by MetaConverter.meta {
            override fun read(source: Meta): Meta = throw failure
        }
        val failingConverter = ZipRowsEnvelopeConverter(cellConverter, typeOf<Meta>())
        val badTail = compressedEnvelope("[{\"value\":42}] false")
        assertFailsWith<SerializationException> { failingConverter.readRows(badTail) }
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            failingConverter.readRowsTo(badTail, maxInflatedBytes = 100) {}
        })
    }

    @Test
    fun testCancellationFromCallbacks(): Unit = runBlocking {
        val envelope = compressedEnvelope("[{\"value\":1},{\"value\":2}]")
        for (cancelInHeader in listOf(true, false)) {
            val failure = CancellationException("Cancel visitor")
            val job = Job()
            var rows = 0
            try {
                val error = assertFailsWith<CancellationException> {
                    withContext(job) {
                        converter.readRowsTo(envelope, maxInflatedBytes = 100, onHeader = {
                            if (cancelInHeader) job.cancel(failure)
                        }) {
                            rows++
                            job.cancel(failure)
                        }
                    }
                }
                assertEquals(failure.message, error.message)
                assertEquals(if (cancelInHeader) 0 else 1, rows)
            } finally {
                job.cancel()
            }
        }
        currentCoroutineContext().ensureActive()
        assertEquals(2L, converter.readRowsTo(compressedEnvelope("[]"), maxInflatedBytes = 2) {})
    }

    @Test
    fun testAlreadyCancelledReadHasNoCallbacks(): Unit = runBlocking {
        val failure = CancellationException("Cancelled before reading")
        val job = Job()
        var callbacks = 0
        try {
            val error = assertFailsWith<CancellationException> {
                withContext(job) {
                    job.cancel(failure)
                    converter.readRowsTo(compressedEnvelope("[]"), maxInflatedBytes = 2, onHeader = {
                        callbacks++
                    }) { callbacks++ }
                }
            }
            assertEquals(failure.message, error.message)
            assertEquals(0, callbacks)
        } finally {
            job.cancel()
        }
        currentCoroutineContext().ensureActive()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testCancellationDuringParserRead() {
        val failure = CancellationException("Cancel parser input")
        val job = Job()
        val source = object : ByteArrayInputStream("[{\"value\":1}]".encodeToByteArray()) {
            var reads = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (++reads == 2) job.cancel(failure)
                return super.read(buffer, offset, minOf(length, 1))
            }
        }
        try {
            assertSame(failure, assertFailsWith<CancellationException> {
                InflatedByteLimitInputStream(source, 100, job).use { input ->
                    Json.decodeToSequence<Map<String, Meta>>(input, DecodeSequenceMode.ARRAY_WRAPPED).forEach {
                        fail("Cancellation happens before the first row")
                    }
                }
            })
            assertEquals(2, source.reads)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun testLimitedStreamCloseClosesSource() {
        val source = object : ByteArrayInputStream(byteArrayOf()) {
            var closed = false
            override fun close() { closed = true }
        }
        InflatedByteLimitInputStream(source, 1).close()
        assertTrue(source.closed)
    }

    @Test
    fun testOrdinaryDecoderBorrowsItsInput() {
        for (body in listOf("[]", "[{")) {
            val input = object : ByteArrayInputStream(body.encodeToByteArray()) {
                var closed = false
                override fun close() { closed = true }
            }
            input.use {
                if (body == "[]") {
                    assertEquals(0, converter.readInflatedRows(Meta.EMPTY, it).rowSequence().count())
                } else {
                    assertFailsWith<SerializationException> { converter.readInflatedRows(Meta.EMPTY, it) }
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
            assertEquals(5L, input.bytesRead)
        }
        assertTrue(source.closed)
        InflatedByteLimitInputStream(ByteArrayInputStream(ByteArray(5)), 5).use { input ->
            assertEquals(5, input.read(ByteArray(10)))
            assertEquals(-1, input.read())
            assertEquals(-1, input.read(ByteArray(1)))
            assertEquals(0, input.read(ByteArray(0)))
            assertEquals(5L, input.bytesRead)
        }
    }
}
