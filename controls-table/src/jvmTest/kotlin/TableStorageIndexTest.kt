package space.kscience.controls.tagtable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import space.kscience.controls.asMeta
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.SingleFileEnvelopeOperations
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope

/**
 * LLM generated code: Added tests for DataPlatformStorageIndex.
 */
class TableStorageIndexTest {

    @Test
    fun testIndexAndRead() = runTest {
        val fixture = WatchFixture("tagtable-index-test", this)
        try {
            val operations = NativeFileEnvelopeOperations(fixture.storage.io)
            val headers = metaHeaders("value")
            val startTime = Instant.fromEpochSeconds(1625097600, 0) // 2021-07-01T00:00:00Z
            val nFiles = 10
            val rowsPerFile = 100

            (0 until nFiles).forEach { i ->
                val fileStartTime = startTime + (i * rowsPerFile).milliseconds
                val fileEndTime = fileStartTime + (rowsPerFile - 1).milliseconds
                val rows = (0 until rowsPerFile).map { j ->
                    tagRow(fileStartTime + j.milliseconds, "value" to (i * rowsPerFile + j).asMeta())
                }
                operations.writeRows("data_$i", fixture.watched, headers, rows, fileStartTime, fileEndTime)
            }

            val index = TableStorageIndex(fixture.storage, fixture.watched)
            index.start()

            val queryRange = (startTime + 50.milliseconds)..(startTime + 150.milliseconds)
            val result = index.selectRows(queryRange).rowSequence().toList()
            assertEquals(101, result.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testRestartRebuildsIndex() = runTest {
        val fixture = WatchFixture("tagtable-index-restart-test", this)
        try {
            val writer = SingleFileEnvelopeOperations(fixture.storage.io)
            val headers = metaHeaders("value")

            val startTime = Instant.fromEpochSeconds(1700000000, 0)
            val nFiles = 5
            val gapMillis = 1000
            val lengthMillis = 500

            // 1. Create multiple files with disjoint, consecutive intervals
            val paths = (0 until nFiles).map { i ->
                val fileStartTime = startTime + (i * gapMillis).milliseconds
                val fileEndTime = fileStartTime + lengthMillis.milliseconds
                val name = "restart_$i"
                writer.writeRows(
                    name,
                    fixture.watched,
                    headers,
                    listOf(tagRow(fileStartTime, "value" to i.asMeta())),
                    fileStartTime,
                    fileEndTime
                )
                fixture.watched.resolve("$name.${FileEnvelopeOperations.FILE_EXTENSION}")
            }

            val fullRange = startTime..(startTime + (nFiles * gapMillis).milliseconds)

            val index = TableStorageIndex(fixture.storage, fixture.watched)

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
            fixture.close()
        }
    }

    @Test
    fun testIndexTracksCreatedAndDeletedEnvelope() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-watch-test", this)
        val native = NativeFileEnvelopeOperations(fixture.storage.io)
        val invalidRead = AtomicBoolean()
        val reads = AtomicInteger()
        val operations = object : FileEnvelopeOperations by native {
            override fun readEnvelope(path: Path): Envelope? {
                if (path.fileName.toString() == "ignored.txt") invalidRead.set(true)
                reads.incrementAndGet()
                return if (Files.exists(path)) native.readEnvelope(path) else null
            }
        }
        val time = Instant.fromEpochSeconds(1625097600)
        val index = TableStorageIndex(
            fixture.storage,
            fixture.watched,
            operations = operations,
            removeFilesCycleDuration = 10.milliseconds,
        )

        try {
            index.start()
            fixture.awaitWatcher(index, time)
            Files.createFile(fixture.watched.resolve("ignored.txt"))
            val filterTime = time + 500.milliseconds
            fixture.publish("filter-sentinel", filterTime)
            index.awaitEnvelopes(filterTime..filterTime)
            assertTrue(!invalidRead.get(), "A non-envelope CREATE event was passed to readEnvelope")

            val sampleTime = time + 1.seconds
            val range = sampleTime..sampleTime
            val path = fixture.publish("created", sampleTime)
            index.awaitEnvelopes(range)
            assertEquals(1, index.selectEnvelopes(range).size)

            Files.delete(path)
            // A missing file must be removed from the index, not just skipped by the reader.
            val removedFromIndex = runCatching {
                awaitUntil(timeout = 5.seconds) {
                    reads.set(0)
                    index.selectEnvelopes(range).isEmpty() && reads.get() == 0
                }
            }.isSuccess
            assertTrue(removedFromIndex, "The deleted envelope remained in the index")
        } finally {
            fixture.close()
        }
    }

    // LLM generated code: Cover recursive indexing and live updates in nested directories.
    @Test
    fun testIndexTracksFilesInExistingAndNewSubdirectories() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-subdirectory-test", this)
        val existingSubdirectory = Files.createDirectory(fixture.watched.resolve("existing"))
        val initialTime = Instant.fromEpochSeconds(1625097600)
        fixture.publishTo(existingSubdirectory, "initial", initialTime)
        val index = TableStorageIndex(
            fixture.storage,
            fixture.watched,
            removeFilesCycleDuration = 10.milliseconds,
        )

        try {
            index.start()
            assertEquals(1, index.selectEnvelopes(initialTime..initialTime).size)

            val existingSubdirectoryTime = initialTime + 1.seconds
            fixture.publishTo(existingSubdirectory, "existing-subdirectory", existingSubdirectoryTime)
            index.awaitEnvelopes(existingSubdirectoryTime..existingSubdirectoryTime)
            assertEquals(1, index.selectEnvelopes(existingSubdirectoryTime..existingSubdirectoryTime).size)

            val newSubdirectory = Files.createDirectory(fixture.watched.resolve("new"))
            val discoveredTime = initialTime + 2.seconds
            fixture.publishTo(newSubdirectory, "discovered", discoveredTime)
            index.awaitEnvelopes(discoveredTime..discoveredTime)

            val createdTime = initialTime + 3.seconds
            val createdPath = fixture.publishTo(newSubdirectory, "created", createdTime)
            index.awaitEnvelopes(createdTime..createdTime)
            assertEquals(1, index.selectEnvelopes(createdTime..createdTime).size)

            Files.delete(createdPath)
            awaitUntil(timeout = 5.seconds) { index.selectEnvelopes(createdTime..createdTime).isEmpty() }
        } finally {
            fixture.close()
        }
    }

    private class WatchFixture(name: String, scope: TestScope) {
        val context = scope.storageContext(name)
        val storage = context.request(ControlsStoragePlugin)
        val directory: Path = Files.createTempDirectory(name)
        val watched: Path = Files.createDirectory(directory.resolve("data"))
        private val staging = Files.createDirectory(directory.resolve("staging"))
        private val writer = SingleFileEnvelopeOperations(storage.io)
        private val headers = metaHeaders("value")

        fun publish(name: String, at: Instant): Path = publishTo(watched, name, at)

        fun publishTo(target: Path, name: String, at: Instant): Path {
            writer.writeRows(name, staging, headers, listOf(tagRow(at, "value" to Meta(1))), at, at, id = name)
            return Files.move(staging.resolve("$name.df"), target.resolve("$name.df"), ATOMIC_MOVE)
        }

        /**
         * Publish probes before [time] until one of them is indexed, which shows that the watcher is registered.
         */
        suspend fun awaitWatcher(index: TableStorageIndex, time: Instant) {
            var attempt = 0
            awaitUntil(timeout = 10.seconds, interval = 50.milliseconds) {
                if (attempt > 0 && index.selectEnvelopes((time - attempt.seconds)..(time - 1.seconds)).isNotEmpty()) {
                    true
                } else {
                    attempt++
                    publish("probe-$attempt", time - attempt.seconds)
                    false
                }
            }
        }

        fun close() {
            context.close()
            directory.toFile().deleteRecursively()
        }
    }

    private suspend fun TableStorageIndex.awaitEnvelopes(range: ClosedRange<Instant>) =
        awaitUntil { selectEnvelopes(range).isNotEmpty() }

    /**
     * Operations that publish [name] while the initial scan runs, before or after the directory is listed.
     */
    private fun WatchFixture.publishingDuringScan(
        name: String,
        at: Instant,
        afterListing: Boolean
    ): FileEnvelopeOperations {
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
        val fixture = WatchFixture("tagtable-index-retry-test", this)
        val missing = fixture.directory.resolve("missing")
        val index = TableStorageIndex(fixture.storage, missing)
        val time = Instant.fromEpochSeconds(1625097600)
        try {
            assertFailsWith<NoSuchFileException> { index.start() }
            Files.move(fixture.watched, missing)
            fixture.publishTo(missing, "created", time)
            assertEquals(1, index.selectEnvelopes(time..time).size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testFileCreatedAfterScanListingIsIndexed() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-after-listing-test", this)
        val time = Instant.fromEpochSeconds(1625097600)
        val operations = fixture.publishingDuringScan("created", time, afterListing = true)
        val index = TableStorageIndex(fixture.storage, fixture.watched, operations = operations)
        try {
            index.start()
            index.awaitEnvelopes(time..time)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testFileFoundByScanAndMonitorIsIndexedOnce() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-scan-and-monitor-test", this)
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
            fixture.close()
        }
    }

    /**
     * A watcher that reports only the keys a test puts into it.
     */
    private class ControlledWatchService : WatchService {
        val keys = LinkedBlockingQueue<WatchKey>()
        override fun close() {}
        override fun poll(): WatchKey? = keys.poll()
        override fun poll(timeout: Long, unit: TimeUnit): WatchKey? = keys.poll(timeout, unit)
        override fun take(): WatchKey = keys.take()
    }

    /**
     * A key that reports an overflow each time it is taken.
     */
    private class OverflowKey : WatchKey {
        override fun isValid(): Boolean = true
        override fun pollEvents(): List<WatchEvent<*>> = listOf(overflowEvent)
        override fun reset(): Boolean = true
        override fun cancel() {}
        override fun watchable(): Watchable = error("Not used by this fixture")
    }

    @Test
    fun testWatchOverflowScansTheDirectoryAgain() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-overflow-test", this)
        val watcher = ControlledWatchService()
        val overflow = OverflowKey()
        val native = NativeFileEnvelopeOperations(fixture.storage.io)
        val scans = AtomicInteger()
        val rescanned = CompletableDeferred<Unit>()
        val operations = object : FileEnvelopeOperations by native {
            override fun envelopeFilesSequence(root: Path): Sequence<Pair<Name, Path>> {
                if (root != fixture.watched) return native.envelopeFilesSequence(root)
                val scan = scans.incrementAndGet()
                return native.envelopeFilesSequence(fixture.watched) + sequence { if (scan == 2) rescanned.complete(Unit) }
            }
        }
        val index = TableStorageIndex(fixture.storage, fixture.watched, operations = operations).apply {
            newWatchService = { watcher }
            registerWatch = { _, _ -> }
        }
        val time = Instant.fromEpochSeconds(1625097600)
        try {
            fixture.publish("indexed", time)
            index.start()
            // the controlled watcher reports no file events, so these files are found only by the next scan
            fixture.publish("first", time)
            fixture.publish("second", time)
            watcher.keys.put(overflow)
            withContext(Dispatchers.IO) {
                withTimeout(10.seconds) { rescanned.await() }
            }
            assertEquals(3, index.selectEnvelopes(time..time).size)
        } finally {
            fixture.close()
        }
    }

    /**
     * Verifies that the `TableStorageIndex` correctly skips envelope intervals that end before the query's
     * start time during a search operation. The test checks if unnecessary reads of interval upper bounds
     * are avoided by counting how many intervals were checked against the query's end bound.
     *
     * This method uses a `WatchFixture` to simulate a storage environment with pre-defined envelope data.
     * These envelopes have start and end times that correspond to a set of sequential timestamps.
     *
     * Key validations:
     * - The `TableStorageIndex` is initialized and queried with a range where no intervals match.
     * - The number of upper-bound interval checks remains within a valid range, confirming that the search
     *   operation prunes irrelevant intervals effectively.
     *
     * Throws:
     * - An assertion error if the pruning logic fails to exclude intervals ending before the query starts
     *   or if excessive upper-bound checks occur.
     */
    @Test
    fun searchPrunesIntervalsEndingBeforeQuery() = runTest {
        val fixture = WatchFixture("tagtable-index-max-end", this)
        val envelopes = (1..6).associate { position ->
            val time = Instant.fromEpochSeconds(position.toLong())
            fixture.directory.resolve("$position.df") to Envelope(Meta {
                set(RowEnvelopeMetaSpec.startTime, time)
                set(RowEnvelopeMetaSpec.endTime, time)
            }, null)
        }
        val operations = object : FileEnvelopeOperations by NativeFileEnvelopeOperations(fixture.storage.io) {
            override fun envelopeFilesSequence(root: Path) = envelopes.keys.asSequence().map { Name.EMPTY to it }
            override fun readEnvelope(path: Path): Envelope? = envelopes[path]
        }
        val index = TableStorageIndex(fixture.storage, fixture.watched, operations = operations)
        var upperBoundReads = 0
        val query = object : ClosedRange<Instant> {
            override val start: Instant = Instant.fromEpochSeconds(10)
            override val endInclusive: Instant
                get() {
                    upperBoundReads++
                    return start
                }
        }

        try {
            index.start()
            assertEquals(emptyList(), index.selectEnvelopes(query))
            @Suppress("KotlinConstantConditions")
            assertTrue(
                upperBoundReads in 1 until envelopes.size,
                "Checked $upperBoundReads of ${envelopes.size} intervals"
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testDeleteSelectsIntervalByPathWhenBoundsAreEqual() = runTest(timeout = 60.seconds) {
        val fixture = WatchFixture("tagtable-index-equal-interval-test", this)
        val targetTime = Instant.fromEpochSeconds(1625097600)
        val reads = AtomicInteger()
        val native = NativeFileEnvelopeOperations(fixture.storage.io)
        val operations = object : FileEnvelopeOperations by native {
            override fun readEnvelope(path: Path): Envelope? {
                reads.incrementAndGet()
                return native.readEnvelope(path)
            }
        }
        val index = TableStorageIndex(
            fixture.storage,
            fixture.watched,
            operations = operations,
            removeFilesCycleDuration = 10.milliseconds,
        )

        try {
            index.start()
            fixture.awaitWatcher(index, targetTime)

            val range = targetTime..targetTime
            val names = listOf("first", "second", "third", "fourth")
            val paths = names.map { fixture.publish(it, targetTime) }
            awaitUntil(timeout = 5.seconds) { index.selectEnvelopes(range).size == names.size }
            assertTrue(paths.all(Files::exists))

            Files.delete(paths[1])
            awaitUntil(timeout = 5.seconds) {
                reads.set(0)
                val remaining = index.selectEnvelopes(range)
                if (reads.get() != 3) return@awaitUntil false
                assertEquals(3, remaining.size, "Deletion removed another equal-bounds interval")
                assertEquals(setOf("first", "third", "fourth"), remaining.map { it.meta["id"].string }.toSet())
                true
            }
        } finally {
            fixture.close()
        }
    }
}
