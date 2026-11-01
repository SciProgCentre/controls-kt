package space.kscience.controls.tagtable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.timeseries.collectTable
import space.kscience.controls.tagtable.timeseries.Meta as TimeMeta
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextBuilder
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.tables.MapRow
import space.kscience.tables.Row
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.TableHeader
import space.kscience.tables.toColumnTable
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// LLM generated code: shared helpers for JVM table tests.
/**
 * A context that runs on the test dispatcher. The context job is not a child of [TestScope.backgroundScope],
 * so it is cancelled explicitly when the background scope is cancelled at the end of the test.
 */
internal fun TestScope.testContext(name: String, block: ContextBuilder.() -> Unit = {}): Context =
    Context(name) {
        coroutineContext(backgroundScope.coroutineContext)
        block()
    }.also { context ->
        backgroundScope.launch {
            try {
                awaitCancellation()
            } finally {
                context.cancel()
            }
        }
    }

internal fun TestScope.storageContext(name: String): Context =
    testContext(name) { plugin(ControlsStoragePlugin) }

internal fun tagRow(time: Instant, vararg values: Pair<String, Meta>): Row<Meta> =
    MapRow(mapOf(TagTable.timeColumnHeader.name to TimeMeta(time), *values))

internal fun metaHeaders(vararg names: String): TableHeader<Meta> =
    listOf(TagTable.timeColumnHeader) + names.map { SimpleColumnHeader(it, typeOf<Meta>(), Meta.EMPTY) }

internal fun FileEnvelopeOperations.writeRows(
    name: String,
    directory: Path,
    headers: TableHeader<Meta>,
    rows: List<Row<Meta>>,
    start: Instant,
    end: Instant,
    id: String? = null,
) = writeEnvelope(
    name,
    directory,
    ZipRowsEnvelopeConverter.meta.writeRows(RowTable(headers, rows), Meta {
        set(RowEnvelopeMetaSpec.startTime, start)
        set(RowEnvelopeMetaSpec.endTime, end)
        if (id != null) set("id", id)
    }),
)

internal suspend fun awaitUntil(
    timeout: Duration = 10.seconds,
    interval: Duration = 10.milliseconds,
    condition: suspend () -> Boolean,
) = withContext(Dispatchers.IO) {
    withTimeout(timeout) {
        while (!condition()) delay(interval)
    }
}

internal val overflowEvent: WatchEvent<Any> = object : WatchEvent<Any> {
    override fun kind(): WatchEvent.Kind<Any> = OVERFLOW
    override fun count(): Int = 1
    override fun context(): Any? = null
}

internal suspend fun assertTimeSeriesHeaders(table: TagTable, expectedQuality: Meta) {
    val qualityName = "sensor" + TagState.TAG_QUALITY_SUFFIX
    for (withQuality in listOf(false, true)) {
        val rows = table.readTimeSeries(1.seconds, withQuality).collectTable(1)
        val expectedHeaders = listOf(TagTable.timeColumnHeader.name, "sensor") +
                if (withQuality) listOf(qualityName) else emptyList()
        assertEquals(expectedHeaders, rows.headers.map { it.name })
        val columns = rows.toColumnTable()
        assertEquals(expectedHeaders, columns.headers.map { it.name })
        if (withQuality) {
            assertEquals(typeOf<Meta>(), rows.headers.last().type)
            assertEquals(expectedQuality, columns.getOrNull(0, qualityName))
        }
    }
}
