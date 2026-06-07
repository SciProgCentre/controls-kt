package space.kscience.controls.dataplatform

import kotlinx.coroutines.test.runTest
import space.kscience.controls.asMeta
import space.kscience.controls.dataplatform.storage.DataPlatformStorageIndex
import space.kscience.controls.dataplatform.storage.RowEnvelopeMetaSpec
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import java.nio.file.Files
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.measureTime

/**
 * LLM generated code: Added tests for DataPlatformStorageIndex.
 */
class DataPlatformStorageIndexTest {

    @Test
    fun testIndexAndRead() = runTest {
        val context = Context("TEST")
        val io = context.io
        val tempDir = Files.createTempDirectory("dataplatform-index-test")

        try {
            val converter = ZipRowsEnvelopeConverter.meta
            val operations = NativeFileEnvelopeOperations(io)

            val headers = listOf(
                DataPlatform.timeColumnHeader,
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
                                DataPlatform.timeColumnHeader.name to space.kscience.controls.dataplatform.timeseries.Meta(
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
            val index = DataPlatformStorageIndex(io, tempDir)
            val indexTime = measureTime {
                index.open()
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

            index.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}