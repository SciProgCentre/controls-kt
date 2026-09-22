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
import java.util.zip.InflaterInputStream
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
    fun testInflatedByteLimitAndHeader(): Unit = runBlocking {
        val header = SimpleColumnHeader<Meta>("value", typeOf<Meta>(), Meta { "unit" put "°C" })
        val value = Meta("Температура 🌡")
        val envelope = converter.writeRows(RowTable(listOf(header), listOf(MapRow(mapOf("value" to value)))))
        val size = InflaterInputStream(ByteArrayInputStream(envelope.data!!.toByteArray())).use { it.readBytes().size.toLong() }
        val error = assertFailsWith<InflatedByteLimitExceededException> {
            converter.readRowsTo(envelope, maxInflatedBytes = size - 1) {}
        }
        assertEquals(size - 1, error.maxInflatedBytes)
        for (limit in listOf(size, size + 1, Long.MAX_VALUE)) {
            val events = mutableListOf<String>()
            val count = converter.readRowsTo(envelope, maxInflatedBytes = limit, onHeader = {
                events += "header"
                val actual = it.single()
                assertEquals(header.name, actual.name)
                assertEquals(header.type, actual.type)
                assertEquals(header.meta, actual.meta)
            }) {
                events += "row"
                assertEquals(value, it.getOrNull("value"))
            }
            assertEquals(size, count)
            assertEquals(listOf("header", "row"), events)
        }
        for (limit in listOf(0L, -1L)) {
            assertFailsWith<IllegalArgumentException> {
                converter.readRowsTo(envelope, maxInflatedBytes = limit) {}
            }
        }
    }

    @Test
    fun testEmptyHeaderAndWhitespaceCount(): Unit = runBlocking {
        val header = SimpleColumnHeader<Meta>("empty", typeOf<Meta>(), Meta { "unit" put "s" })
        val envelope = converter.writeRows(RowTable<Meta>(listOf(header), emptyList()))
        var headerCalls = 0
        assertEquals(2L, converter.readRowsTo(envelope, maxInflatedBytes = 2, onHeader = {
            headerCalls++
            assertEquals(header.name, it.single().name)
            assertEquals(header.meta, it.single().meta)
        }) { fail("Empty array has no rows") })
        assertEquals(1, headerCalls)

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
    fun testWrittenOrderAndOrdinaryReplay(): Unit = runBlocking {
        val body = "[{\"value\":3},{\"value\":1},{\"value\":2}]"
        val envelope = compressedEnvelope(body)
        val actual = mutableListOf<Meta?>()
        assertEquals(body.length.toLong(), converter.readRowsTo(envelope, maxInflatedBytes = 100) {
            actual += it.getOrNull("value")
        })
        assertEquals(listOf<Meta?>(Meta(3), Meta(1), Meta(2)), actual)
        val ordinary = converter.readRows(envelope)
        repeat(2) { assertEquals(actual, ordinary.rowSequence().map { it.getOrNull("value") }.toList()) }
        assertFailsWith<InflatedByteLimitExceededException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 1) {}
        }
        assertEquals(3, converter.readRows(envelope).rowSequence().count())
    }

    @Test
    fun testQueryBudgetUsesActualCounts(): Unit = runBlocking {
        val bodies = listOf("[{\"value\":1}]", "[{\"value\":2},{\"value\":3}]")
        val envelopes = bodies.map(::compressedEnvelope)
        val total = bodies.sumOf { it.encodeToByteArray().size.toLong() }
        val perEnvelope = bodies.maxOf { it.length }.toLong() + 5
        for (queryLimit in listOf(total, total + 1)) {
            var remaining = queryLimit
            val values = mutableListOf<Meta?>()
            for (envelope in envelopes) {
                val count = converter.readRowsTo(envelope, maxInflatedBytes = minOf(perEnvelope, remaining)) {
                    values += it.getOrNull("value")
                }
                remaining -= count
            }
            assertEquals(queryLimit - total, remaining)
            assertEquals(listOf<Meta?>(Meta(1), Meta(2), Meta(3)), values)
        }
        var remaining = total - 1
        var calls = 0
        val error = assertFailsWith<InflatedByteLimitExceededException> {
            for (envelope in envelopes + compressedEnvelope("[]")) {
                calls++
                remaining -= converter.readRowsTo(envelope, maxInflatedBytes = minOf(perEnvelope, remaining)) {}
            }
        }
        assertEquals(2, calls)
        assertEquals(bodies.last().length - 1L, error.maxInflatedBytes)
        assertEquals(total - 1 - bodies.first().length, remaining)
    }

    @Test
    fun testLateFailureDoesNotPublishAccumulatedRows(): Unit = runBlocking {
        val collected = mutableListOf<Meta?>()
        var published: List<Meta?>? = null
        assertFailsWith<SerializationException> {
            for (envelope in listOf(compressedEnvelope("[{\"value\":1}]"), compressedEnvelope("[{\"value\":2}] false"))) {
                converter.readRowsTo(envelope, maxInflatedBytes = 100) { collected += it.getOrNull("value") }
            }
            published = collected.toList()
        }
        assertEquals(listOf<Meta?>(Meta(1), Meta(2)), collected)
        assertNull(published)
    }

    @Test
    fun testJsonAndCompressedTail(): Unit = runBlocking {
        for (body in listOf("[] false", "[] []", "[{", "[{},]")) {
            val envelope = compressedEnvelope(body)
            assertFailsWith<SerializationException> { converter.readRows(envelope) }
            assertFailsWith<SerializationException> {
                converter.readRowsTo(envelope, maxInflatedBytes = 100) {}
            }
        }
        val envelope = compressedEnvelope("[]")
        val bytes = envelope.data!!.toByteArray()
        val truncated = Envelope(envelope.meta, bytes.copyOf(bytes.size - 2).asBinary())
        assertFailsWith<IOException> { converter.readRows(truncated) }
        val error = assertFailsWith<IOException> { converter.readRowsTo(truncated, maxInflatedBytes = 100) {} }
        assertFalse(error is InflatedByteLimitExceededException)

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
    fun testCallbackAndConverterFailures(): Unit = runBlocking {
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
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 100, onHeader = { throw failure }) {
                fail("Header callback failed")
            }
        })
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            converter.readRowsTo(envelope, maxInflatedBytes = 100) { throw failure }
        })

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
    fun testCancellationDuringParserReadClosesSource() {
        val failure = CancellationException("Cancel parser input")
        val job = Job()
        val source = object : ByteArrayInputStream("[{\"value\":1}]".encodeToByteArray()) {
            var closed = false
            var reads = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (++reads == 2) job.cancel(failure)
                return super.read(buffer, offset, minOf(length, 1))
            }
            override fun close() { closed = true }
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
            assertTrue(source.closed)
        } finally {
            job.cancel()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testParserClosesOwnedWrapperOnEveryOutcome() {
        class TrackedInput(body: String) : ByteArrayInputStream(body.encodeToByteArray()) {
            var closed = false
            override fun close() { closed = true }
        }
        fun read(source: TrackedInput, limit: Long) {
            InflatedByteLimitInputStream(source, limit).use { input ->
                Json.decodeToSequence<Map<String, Meta>>(input, DecodeSequenceMode.ARRAY_WRAPPED).toList()
            }
        }
        val valid = TrackedInput("[]")
        read(valid, 2)
        assertTrue(valid.closed)
        val malformed = TrackedInput("[] false")
        assertFailsWith<SerializationException> { read(malformed, 100) }
        assertTrue(malformed.closed)
        val oversized = TrackedInput("[]")
        assertFailsWith<InflatedByteLimitExceededException> { read(oversized, 1) }
        assertTrue(oversized.closed)
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
