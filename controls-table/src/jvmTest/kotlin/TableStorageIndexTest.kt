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
import space.kscience.dataforge.meta.set
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun testIndexTracksCreatedAndDeletedEnvelope() = runTest(timeout = 60.seconds) {
        val context = Context("tagtable-index-watch-test") { plugin(ControlsStoragePlugin) }
        val storage = context.request(ControlsStoragePlugin)
        val directory = Files.createTempDirectory("tagtable-index-watch-test")
        val watched = Files.createDirectory(directory.resolve("data"))
        val staging = Files.createDirectory(directory.resolve("staging"))
        val native = NativeFileEnvelopeOperations(storage.io)
        val reads = AtomicInteger()
        val operations = object : FileEnvelopeOperations by native {
            override fun readEnvelope(path: Path): Envelope? {
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
}
