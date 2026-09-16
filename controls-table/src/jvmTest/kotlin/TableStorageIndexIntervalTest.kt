package space.kscience.controls.tagtable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.meta.string
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TableStorageIndexIntervalTest {

    @Test
    fun testDeleteSelectsIntervalByPathWhenBoundsAreEqual() = runTest(timeout = 60.seconds) {
        val context = Context("tagtable-index-equal-interval-test") { plugin(ControlsStoragePlugin) }
        val storage = context.request(ControlsStoragePlugin)
        val directory = Files.createTempDirectory("tagtable-index-equal-interval-test")
        val watched = Files.createDirectory(directory.resolve("data"))
        val staging = Files.createDirectory(directory.resolve("staging"))
        val delegate = NativeFileEnvelopeOperations(storage.io)
        val reads = AtomicInteger()
        val operations = object : FileEnvelopeOperations by delegate {
            override fun readEnvelope(path: Path): Envelope? {
                reads.incrementAndGet()
                return delegate.readEnvelope(path)
            }
        }
        val targetTime = Instant.fromEpochSeconds(1625097600)
        val index = TableStorageIndex(
            storage,
            watched,
            operations = operations,
            removeFilesCycleDuration = 10.milliseconds,
        )

        fun publish(name: String, time: Instant): Path {
            val envelope = Envelope(Meta {
                set(RowEnvelopeMetaSpec.startTime, time)
                set(RowEnvelopeMetaSpec.endTime, time)
                "id" put name
            }, null)
            delegate.writeEnvelope(name, staging, envelope)
            val fileName = "$name${delegate.metaExtension}"
            return Files.move(staging.resolve(fileName), watched.resolve(fileName), ATOMIC_MOVE)
        }

        try {
            index.start()
            withContext(Dispatchers.IO) {
                // Signal that the watcher is registered without relying on timing after start().
                withTimeout(10.seconds) {
                    var attempt = 0
                    while (true) {
                        val probeTime = targetTime - (++attempt).seconds
                        publish("probe-$attempt", probeTime)
                        delay(50.milliseconds)
                        if (index.selectEnvelopes(probeTime..probeTime).isNotEmpty()) break
                    }
                }

                val range = targetTime..targetTime
                val names = listOf("first", "second", "third", "fourth")
                val paths = mutableMapOf<String, Path>()
                names.forEachIndexed { position, name ->
                    paths[name] = publish(name, targetTime)
                    withTimeout(5.seconds) {
                        while (index.selectEnvelopes(range).size != position + 1) {
                            delay(10.milliseconds)
                        }
                    }
                }
                assertTrue(paths.values.all(Files::exists))

                Files.delete(paths.getValue("second"))
                withTimeout(5.seconds) {
                    while (true) {
                        reads.set(0)
                        val remaining = index.selectEnvelopes(range)
                        if (reads.get() == 3) {
                            assertEquals(3, remaining.size, "Deletion removed another equal-bounds interval")
                            assertEquals(
                                setOf("first", "third", "fourth"),
                                remaining.map { it.meta["id"].string }.toSet(),
                            )
                            break
                        }
                        delay(10.milliseconds)
                    }
                }
            }
        } finally {
            index.stop()
            context.coroutineContext[Job]?.cancelAndJoin()
            context.close()
            directory.toFile().deleteRecursively()
        }
    }
}
