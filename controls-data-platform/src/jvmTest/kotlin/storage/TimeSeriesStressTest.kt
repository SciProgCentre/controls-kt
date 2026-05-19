package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import space.kscience.attributes.safeTypeOf
import space.kscience.controls.asMeta
import space.kscience.controls.dataplatform.DataPlatform
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.dataplatform.timeseries.collectTable
import space.kscience.controls.instant
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Global
import space.kscience.dataforge.io.TaggedEnvelopeFormat
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import java.io.File
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.time.Instant

/*
 * LLM generated code
 * Stress test for TimeSeriesRows compression and serialization.
 * Measures file size and performance for different compression scenarios.
 */
class TimeSeriesStressTest {

    private val columnsCount = 50
    private val rowsCount = 2000

    private val testHeaders: TableHeader<Meta> = listOf(DataPlatform.timeColumnHeader) + (0 until columnsCount).map { j ->
        SimpleColumnHeader("v$j", typeOf<Meta>(), Meta.EMPTY)
    }

    private fun generateRandomWalkData(): List<TimeSeriesValues<Meta>> {
        val random = Random(0)
        val currentValues = DoubleArray(columnsCount) { 0.0 }
        return List(rowsCount) { i ->
            val time = Instant.fromEpochMilliseconds(i * 1000L)
            val map = mutableMapOf<String, Meta>()
            // Using the same column name as in DataPlatform.timeColumnHeader
            map[DataPlatform.timeColumnHeader.name] = MetaConverter.instant.convert(time)
            for (j in 0 until columnsCount) {
                currentValues[j] += random.nextDouble(-0.1, 0.1)
                map["v$j"] = currentValues[j].asMeta()
            }
            ValueWithTime(map, time)
        }
    }

    private fun createTimeSeriesRows(rows: List<TimeSeriesValues<Meta>>): TimeSeriesRows<Meta> =
        object : TimeSeriesRows<Meta> {
            override val headers: TableHeader<Meta> = testHeaders
            override fun subscribe() = rows.asFlow()
        }

    private suspend fun runScenario(
        name: String,
        rows: List<TimeSeriesValues<Meta>>,
        rowsCompression: RowsCompression?
    ) {
        val timeSeriesRows = createTimeSeriesRows(rows)
        val compressed = if (rowsCompression == null) {
            timeSeriesRows
        } else {
            timeSeriesRows.compress(rowsCompression)
        }

        // Measure collection time
        val collectStartTime = System.nanoTime()
        val table = compressed.collectTable(rowsCount)
        val collectEndTime = System.nanoTime()

        val converter = ZipRowsEnvelopeConverter(MetaConverter.meta, safeTypeOf<Meta>())
        val envelopeFormat = TaggedEnvelopeFormat(Global.io)

        // Write to file
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("stress-test-$name", ".df")
        }
        val writeStartTime = System.nanoTime()
        val envelope = converter.writeRows(table)
        file.outputStream().asSink().buffered().use {
            envelopeFormat.writeTo(it, envelope)
        }
        val writeEndTime = System.nanoTime()

        val fileSize = file.length()

        // Read from file
        val readStartTime = System.nanoTime()
        val readEnvelope = file.inputStream().asSource().buffered().use {
            envelopeFormat.readFrom(it)
        }
        val resultTable = converter.readRows(readEnvelope)
        // Consume rows to ensure they are read
        val resultRowsCount = resultTable.rowSequence().count()
        val readEndTime = System.nanoTime()

        println("""
            
            Scenario: $name
            File size: ${fileSize / 1024.0} KB
            Write time: ${(writeEndTime - writeStartTime) / 1_000_000.0} ms
            Read time: ${(readEndTime - readStartTime) / 1_000_000.0} ms
            Number of rows in result: $resultRowsCount
        """.trimIndent())

        file.delete()
    }

    @Test
    fun testStress() = runTest {
        val data = generateRandomWalkData()

        runScenario("No compression", data, null)

        runScenario("Skip repeating values", data, RowsCompression(
            skipUnchangedRows = false,
            skipUnchangedValues = true,
            numericDelta = null
        ))

        runScenario("Skip values within 0.1 margin", data, RowsCompression(
            skipUnchangedRows = false,
            skipUnchangedValues = true,
            numericDelta = 0.1
        ))
    }
}
