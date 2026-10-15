package space.kscience.controls.tagtable

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TableStorageIndexMaxEndTest {
    @Test
    fun searchPrunesIntervalsEndingBeforeQuery() = runTest {
        val context = Context("tagtable-index-max-end") { plugin(ControlsStoragePlugin) }
        val storage = context.request(ControlsStoragePlugin)
        val directory = Files.createTempDirectory("tagtable-index-max-end")
        val envelopes = (1..6).associate { position ->
            val time = Instant.fromEpochSeconds(position.toLong())
            directory.resolve("$position.df") to Envelope(Meta {
                set(RowEnvelopeMetaSpec.startTime, time)
                set(RowEnvelopeMetaSpec.endTime, time)
            }, null)
        }
        val operations = object : FileEnvelopeOperations by NativeFileEnvelopeOperations(storage.io) {
            override fun envelopeFilesSequence(root: Path) =
                envelopes.keys.asSequence().map { Name.EMPTY to it }

            override fun readEnvelope(path: Path): Envelope? = envelopes[path]
        }
        val index = TableStorageIndex(storage, directory, operations = operations)

        var upperBoundReads = 0
        val query = object : ClosedRange<Instant> {
            override val start: Instant = Instant.fromEpochSeconds(10)
            // Each visited node compares its start with this bound.
            override val endInclusive: Instant
                get() {
                    upperBoundReads++
                    return start
                }
        }

        try {
            index.start()
            assertEquals(emptyList(), index.selectEnvelopes(query))
            assertTrue(upperBoundReads in 1 until envelopes.size, "Checked $upperBoundReads of ${envelopes.size} intervals")
        } finally {
            index.stop()
            context.coroutineContext[Job]?.cancelAndJoin()
            context.close()
            directory.toFile().deleteRecursively()
        }
    }
}
