package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.kscience.controls.asMeta
import space.kscience.controls.dataplatform.DataPlatformDevice
import space.kscience.controls.dataplatform.timeseries.TimeSeriesRows
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
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
        DataPlatformDevice.timeColumnHeader,
        SimpleColumnHeader("v1", typeOf<Meta>(), Meta.EMPTY),
        SimpleColumnHeader("v2", typeOf<Meta>(), Meta.EMPTY)
    )

    private fun createAsyncRows(rows: List<TimeSeriesValues<Meta>>): TimeSeriesRows<Meta> =
        object : TimeSeriesRows<Meta> {
            override val headers: TableHeader<Meta> = testHeaders
            override fun subscribe() = rows.asFlow()
        }

    @Test
    fun testSkipUnchangedRows() = runTest {
        val rows = listOf(
            ValueWithTime(
                mapOf(
                    "v1" to 10.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(1000)
            ),
            ValueWithTime(
                mapOf(
                    "v1" to 10.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(2000)
            ), // Duplicate
            ValueWithTime(
                mapOf(
                    "v1" to 11.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(3000)
            )
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
            ValueWithTime(
                mapOf(
                    "v1" to 10.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(1000)
            ),
            ValueWithTime(
                mapOf(
                    "v1" to 10.0.asMeta(),
                    "v2" to 21.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(2000)
            ), // v1 unchanged
            ValueWithTime(
                mapOf(
                    "v1" to 11.0.asMeta(),
                    "v2" to 21.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(3000)
            )  // v2 unchanged
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
            ValueWithTime(mapOf("v1" to 10.0.asMeta()), Instant.fromEpochMilliseconds(1000)),
            ValueWithTime(
                mapOf(
                    "v1" to 10.5.asMeta()
                ),
                Instant.fromEpochMilliseconds(2000)
            ), // delta 0.5 <= 1.0, should skip only this value
            ValueWithTime(
                mapOf(
                    "v1" to 11.1.asMeta()
                ),
                Instant.fromEpochMilliseconds(3000)
            )  // delta 1.1 > 1.0, should keep
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
            ValueWithTime(
                mapOf(
                    "v1" to 10.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(1000)
            ),
            ValueWithTime(
                mapOf(
                    "v1" to 10.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(2000)
            ), // identical row
            ValueWithTime(
                mapOf(
                    "v1" to 11.0.asMeta(),
                    "v2" to 20.0.asMeta()
                ),
                Instant.fromEpochMilliseconds(3000)
            )  // only v1 changed
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