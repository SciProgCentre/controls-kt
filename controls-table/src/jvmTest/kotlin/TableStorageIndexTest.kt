package space.kscience.controls.tagtable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import space.kscience.controls.asMeta
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.SingleFileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime

/**
 * LLM generated code: Added tests for DataPlatformStorageIndex.
 */
class TableStorageIndexTest {

    @Test
    fun testIndexAndRead() = runTest {
        val context = Context("TEST"){
            plugin(ControlsStoragePlugin)
        }
        val storagePlugin = context.request(ControlsStoragePlugin)
        val tempDir = Files.createTempDirectory("tagtable-index-test")

        try {
            val converter = ZipRowsEnvelopeConverter.meta
            val operations = NativeFileEnvelopeOperations(storagePlugin.io)

            val headers = listOf(
                TagTable.timeColumnHeader,
                SimpleColumnHeader("value", typeOf<Meta>(), Meta.EMPTY)
            )

            val startTime = Instant.fromEpochSeconds(1625097600, 0) // 2021-07-01T00:00:00Z
            val nFiles = 10
            val rowsPerFile = 100

            // 1. Create multiple files
            val creationTime = measureTime {
                (0 until nFiles).forEach { i ->
                    val fileStartTime = startTime + (i * rowsPerFile).milliseconds
                    val fileEndTime = fileStartTime + (rowsPerFile - 1).milliseconds
                    val rows = (0 until rowsPerFile).map { j ->
                        MapRow(
                            mapOf(
                                TagTable.timeColumnHeader.name to space.kscience.controls.tagtable.timeseries.Meta(
                                    fileStartTime + j.milliseconds
                                ),
                                "value" to (i * rowsPerFile + j).asMeta()
                            )
                        )
                    }
                    val table = RowTable(headers, rows)
                    val envelopeMeta = Meta {
                        set(RowEnvelopeMetaSpec.startTime, fileStartTime)
                        set(RowEnvelopeMetaSpec.endTime, fileEndTime)
                    }
                    val envelope = converter.writeRows(table, envelopeMeta)
                    operations.writeEnvelope("data_$i", tempDir, envelope)
                }
            }
            println("[DEBUG_LOG] Created $nFiles files in $creationTime")

            // 2. Index files
            val index = TableStorageIndex(storagePlugin, tempDir)
            val indexTime = measureTime {
                index.start()
            }
            println("[DEBUG_LOG] Indexed $nFiles files in $indexTime")

            // 3. Read from index
            val queryRange = (startTime + 50.milliseconds)..(startTime + 150.milliseconds)
            val readTime = measureTime {
                val rows = index.selectRows(queryRange)
                val result = rows.rowSequence().toList()
                // startTime+50 to startTime+150 inclusive is 101 points
                assertEquals(101, result.size)
            }
            println("[DEBUG_LOG] Read $queryRange in $readTime")

            index.stop()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testRestartRebuildsIndex() = runTest {
        val context = Context("tagtable-index-restart-test") {
            plugin(ControlsStoragePlugin)
        }
        val storagePlugin = context.request(ControlsStoragePlugin)
        val tempDir = Files.createTempDirectory("tagtable-index-restart-test")

        try {
            val converter = ZipRowsEnvelopeConverter.meta
            val writer = SingleFileEnvelopeOperations(storagePlugin.io)

            val headers = listOf(
                TagTable.timeColumnHeader,
                SimpleColumnHeader("value", typeOf<Meta>(), Meta.EMPTY)
            )

            val startTime = Instant.fromEpochSeconds(1700000000, 0)
            val nFiles = 5
            val gapMillis = 1000
            val lengthMillis = 500

            // 1. Create multiple files with disjoint, consecutive intervals
            val paths = (0 until nFiles).map { i ->
                val fileStartTime = startTime + (i * gapMillis).milliseconds
                val fileEndTime = fileStartTime + lengthMillis.milliseconds
                val row = MapRow(
                    mapOf(
                        TagTable.timeColumnHeader.name to space.kscience.controls.tagtable.timeseries.Meta(fileStartTime),
                        "value" to i.asMeta()
                    )
                )
                val table = RowTable(headers, listOf(row))
                val envelopeMeta = Meta {
                    set(RowEnvelopeMetaSpec.startTime, fileStartTime)
                    set(RowEnvelopeMetaSpec.endTime, fileEndTime)
                }
                val envelope = converter.writeRows(table, envelopeMeta)
                val name = "restart_$i"
                writer.writeEnvelope(name, tempDir, envelope)
                tempDir.resolve("$name.${FileEnvelopeOperations.FILE_EXTENSION}")
            }

            val fullRange = startTime..(startTime + (nFiles * gapMillis).milliseconds)

            val index = TableStorageIndex(storagePlugin, tempDir)

            // 2. Start, check all files are indexed, then stop
            index.start()
            assertEquals(nFiles, index.selectEnvelopes(fullRange).size)
            index.stop()

            // 3. Remove one file while the index is stopped
            Files.delete(paths[2])

            // 4. Restart and check the tree was rebuilt, not appended to
            index.start()
            val envelopes = index.selectEnvelopes(fullRange)
            assertEquals(nFiles - 1, envelopes.size)
            val startTimes = envelopes.mapNotNull { it.meta[RowEnvelopeMetaSpec.startTime] }
            assertEquals(envelopes.size, startTimes.distinct().size, "Duplicate intervals found after restart")

            index.stop()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testIndexTracksCreatedAndDeletedEnvelope() = runTest(timeout = 60.seconds) {
        val context = Context("tagtable-index-watch-test") { plugin(ControlsStoragePlugin) }
        val storage = context.request(ControlsStoragePlugin)
        val directory = Files.createTempDirectory("tagtable-index-watch-test")
        val watched = Files.createDirectory(directory.resolve("data"))
        val staging = Files.createDirectory(directory.resolve("staging"))
        val native = NativeFileEnvelopeOperations(storage.io)
        val invalidRead = AtomicBoolean()
        val reads = AtomicInteger()
        val operations = object : FileEnvelopeOperations by native {
            override fun readEnvelope(path: Path): Envelope? {
                if (path.fileName.toString() == "ignored.txt") invalidRead.set(true)
                reads.incrementAndGet()
                return if (Files.exists(path)) native.readEnvelope(path) else null
            }
        }
        val writer = SingleFileEnvelopeOperations(storage.io)
        val converter = ZipRowsEnvelopeConverter.meta
        val time = Instant.fromEpochSeconds(1625097600)
        val headers = listOf(TagTable.timeColumnHeader, SimpleColumnHeader("value", typeOf<Meta>(), Meta.EMPTY))
        val index = TableStorageIndex(storage, watched, operations = operations, removeFilesCycleDuration = 10.milliseconds)

        fun publish(name: String, at: Instant): Path {
            val row = MapRow(mapOf(
                TagTable.timeColumnHeader.name to space.kscience.controls.tagtable.timeseries.Meta(at),
                "value" to Meta(1),
            ))
            val envelope = converter.writeRows(RowTable(headers, listOf(row)), Meta {
                set(RowEnvelopeMetaSpec.startTime, at)
                set(RowEnvelopeMetaSpec.endTime, at)
            })
            writer.writeEnvelope(name, staging, envelope)
            return Files.move(staging.resolve("$name.df"), watched.resolve("$name.df"), ATOMIC_MOVE)
        }

        try {
            index.start()
            withContext(Dispatchers.IO) {
                withTimeout(10.seconds) {
                    var attempt = 0
                    do {
                        publish("probe-${attempt++}", time)
                        delay(50.milliseconds)
                    } while (index.selectEnvelopes(time..time).isEmpty())
                }

                Files.createFile(watched.resolve("ignored.txt"))
                val filterTime = time + 500.milliseconds
                publish("filter-sentinel", filterTime)
                withTimeout(5.seconds) {
                    while (index.selectEnvelopes(filterTime..filterTime).isEmpty()) delay(10.milliseconds)
                }
                assertTrue(!invalidRead.get(), "A non-envelope CREATE event was passed to readEnvelope")

                val sampleTime = time + 1.seconds
                val range = sampleTime..sampleTime
                val path = publish("created", sampleTime)
                withTimeout(5.seconds) {
                    while (index.selectEnvelopes(range).isEmpty()) delay(10.milliseconds)
                }
                assertEquals(1, index.selectEnvelopes(range).size)

                Files.delete(path)
                // A missing file must be removed from the index, not just skipped by the reader.
                val removedFromIndex = withTimeoutOrNull(5.seconds) {
                    do {
                        reads.set(0)
                        assertTrue(index.selectEnvelopes(range).isEmpty())
                        if (reads.get() != 0) delay(10.milliseconds)
                    } while (reads.get() != 0)
                    true
                }
                assertTrue(removedFromIndex == true, "The deleted envelope remained in the index")
            }
        } finally {
            index.stop()
            context.coroutineContext[Job]?.cancelAndJoin()
            context.close()
            directory.toFile().deleteRecursively()
        }
    }

    private class WatchFixture(name: String) {
        val context = Context(name) { plugin(ControlsStoragePlugin) }
        val storage = context.request(ControlsStoragePlugin)
        val directory: Path = Files.createTempDirectory(name)
        val watched: Path = Files.createDirectory(directory.resolve("data"))
        private val staging = Files.createDirectory(directory.resolve("staging"))
        private val writer = SingleFileEnvelopeOperations(storage.io)
        private val headers = listOf(TagTable.timeColumnHeader, SimpleColumnHeader("value", typeOf<Meta>(), Meta.EMPTY))

        fun publish(name: String, at: Instant) = publishTo(watched, name, at)

        fun publishTo(target: Path, name: String, at: Instant) {
            val row = MapRow(mapOf(
                TagTable.timeColumnHeader.name to space.kscience.controls.tagtable.timeseries.Meta(at),
                "value" to Meta(1),
            ))
            val envelope = ZipRowsEnvelopeConverter.meta.writeRows(RowTable(headers, listOf(row)), Meta {
                set(RowEnvelopeMetaSpec.startTime, at)
                set(RowEnvelopeMetaSpec.endTime, at)
            })
            writer.writeEnvelope(name, staging, envelope)
            Files.move(staging.resolve("$name.df"), target.resolve("$name.df"), ATOMIC_MOVE)
        }

        suspend fun close(index: TableStorageIndex) {
            index.stop()
            context.coroutineContext[Job]?.cancelAndJoin()
            context.close()
            directory.toFile().deleteRecursively()
        }
    }

    private suspend fun TableStorageIndex.awaitEnvelopes(range: ClosedRange<Instant>) = withContext(Dispatchers.IO) {
        withTimeout(10.seconds) {
            while (selectEnvelopes(range).isEmpty()) delay(10.milliseconds)
        }
    }

    /**
     * Operations that publish [name] while the initial scan runs, before or after the directory is listed.
     */
    private fun WatchFixture.publishingDuringScan(name: String, at: Instant, afterListing: Boolean): FileEnvelopeOperations {
        val native = NativeFileEnvelopeOperations(storage.io)
        val scans = AtomicInteger()
        return object : FileEnvelopeOperations by native {
            override fun envelopeFilesSequence(root: Path): Sequence<Pair<Name, Path>> {
                if (root != watched || scans.getAndIncrement() != 0) return native.envelopeFilesSequence(root)
                if (!afterListing) publish(name, at)
                return native.envelopeFilesSequence(root) + sequence { if (afterListing) publish(name, at) }
            }
        }
    }

    @Test
    fun testFailedStartIsRetriedByTheNextQuery() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-retry-test")
        val missing = fixture.directory.resolve("missing")
        val index = TableStorageIndex(fixture.storage, missing)
        val time = Instant.fromEpochSeconds(1625097600)
        try {
            assertFailsWith<NoSuchFileException> { index.start() }
            Files.move(fixture.watched, missing)
            fixture.publishTo(missing, "created", time)
            assertEquals(1, index.selectEnvelopes(time..time).size)
        } finally {
            fixture.close(index)
        }
    }

    @Test
    fun testFileCreatedAfterScanListingIsIndexed() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-after-listing-test")
        val time = Instant.fromEpochSeconds(1625097600)
        val operations = fixture.publishingDuringScan("created", time, afterListing = true)
        val index = TableStorageIndex(fixture.storage, fixture.watched, operations = operations)
        try {
            index.start()
            index.awaitEnvelopes(time..time)
        } finally {
            fixture.close(index)
        }
    }

    @Test
    fun testFileFoundByScanAndMonitorIsIndexedOnce() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-scan-and-monitor-test")
        val time = Instant.fromEpochSeconds(1625097600)
        val operations = fixture.publishingDuringScan("created", time, afterListing = false)
        val index = TableStorageIndex(fixture.storage, fixture.watched, operations = operations)
        try {
            index.start()
            // events are handled in order, so the sentinel shows that the event for the first file was handled too
            val sentinelTime = time + 1.seconds
            fixture.publish("sentinel", sentinelTime)
            index.awaitEnvelopes(sentinelTime..sentinelTime)
            assertEquals(1, index.selectEnvelopes(time..time).size)
        } finally {
            fixture.close(index)
        }
    }
}
