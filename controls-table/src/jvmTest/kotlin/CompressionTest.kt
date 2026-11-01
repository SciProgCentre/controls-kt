package space.kscience.controls.tagtable

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.kscience.controls.asMeta
import space.kscience.controls.tagtable.timeseries.TimeSeriesRows
import space.kscience.controls.tagtable.timeseries.TimeSeriesValues
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.meta.Meta
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CompressionTest {

    private val testHeaders: TableHeader<Meta> = listOf(
        TagTable.timeColumnHeader,
        SimpleColumnHeader("v1", typeOf<Meta>(), Meta.EMPTY),
        SimpleColumnHeader("v2", typeOf<Meta>(), Meta.EMPTY)
    )

    private fun createAsyncRows(rows: List<TimeSeriesValues<Meta>>): TimeSeriesRows<Meta> =
        object : TimeSeriesRows<Meta> {
            override val headers: TableHeader<Meta> = testHeaders
            override fun subscribe() = rows.asFlow()
        }

    private fun row(time: Long, vararg values: Pair<String, Double>) =
        ValueWithTime(values.associate { (name, value) -> name to value.asMeta() }, Instant.fromEpochMilliseconds(time))

    @Test
    fun testSkipUnchangedRows() = runTest {
        val rows = listOf(
            row(1000, "v1" to 10.0, "v2" to 20.0),
            row(2000, "v1" to 10.0, "v2" to 20.0), // Duplicate
            row(3000, "v1" to 11.0, "v2" to 20.0),
        )
        val asyncRows = createAsyncRows(rows)
        val compressed = asyncRows.compress(RowsCompression(skipUnchangedRows = true))
        val result = compressed.subscribe().toList()

        assertEquals(2, result.size)
        assertEquals(10.0.asMeta(), result[0].value["v1"])
        assertEquals(11.0.asMeta(), result[1].value["v1"])
    }

    @Test
    fun testSkipUnchangedValues() = runTest {
        val rows = listOf(
            row(1000, "v1" to 10.0, "v2" to 20.0),
            row(2000, "v1" to 10.0, "v2" to 21.0), // v1 unchanged
            row(3000, "v1" to 11.0, "v2" to 21.0), // v2 unchanged
        )
        val asyncRows = createAsyncRows(rows)
        val compressed = asyncRows.compress(RowsCompression(skipUnchangedRows = false, skipUnchangedValues = true))
        val result = compressed.subscribe().toList()

        assertEquals(3, result.size)

        // Row 1: all values present
        assertEquals(10.0.asMeta(), result[0].value["v1"])
        assertEquals(20.0.asMeta(), result[0].value["v2"])

        // Row 2: v1 skipped
        assertEquals(null, result[1].value["v1"])
        assertEquals(21.0.asMeta(), result[1].value["v2"])

        // Row 3: v2 skipped
        assertEquals(11.0.asMeta(), result[2].value["v1"])
        assertEquals(null, result[2].value["v2"])
    }

    @Test
    fun testNumericDelta() = runTest {
        val rows = listOf(
            row(1000, "v1" to 10.0),
            row(2000, "v1" to 10.5), // delta 0.5 <= 1.0, should skip only this value
            row(3000, "v1" to 11.1), // delta 1.1 > 1.0, should keep
        )
        val asyncRows = createAsyncRows(rows)
        val compressed = asyncRows.compress(
            RowsCompression(
                skipUnchangedRows = false,
                columns = mapOf("v1" to ColumnCompression(numericDelta = 1.0))
            )
        )
        val result = compressed.subscribe().toList()

        assertEquals(3, result.size)
        assertEquals(10.0.asMeta(), result[0].value["v1"])
        assertEquals(11.1.asMeta(), result[2].value["v1"])
    }

    @Test
    fun testMixedCompression() = runTest {
        val rows = listOf(
            row(1000, "v1" to 10.0, "v2" to 20.0),
            row(2000, "v1" to 10.0, "v2" to 20.0), // identical row
            row(3000, "v1" to 11.0, "v2" to 20.0), // only v1 changed
        )
        val asyncRows = createAsyncRows(rows)
        val compressed = asyncRows.compress(
            RowsCompression(
                skipUnchangedRows = true,
                skipUnchangedValues = true
            )
        )
        val result = compressed.subscribe().toList()

        assertEquals(2, result.size)
        // Row 1: all present
        assertEquals(10.0.asMeta(), result[0].value["v1"])
        assertEquals(20.0.asMeta(), result[0].value["v2"])

        // Row 2: skipped because skipUnchangedRows is true

        // Row 3: v2 skipped because skipUnchangedValues is true
        assertEquals(11.0.asMeta(), result[1].value["v1"])
        assertEquals(null, result[1].value["v2"])
    }
}