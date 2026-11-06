package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.tagtable.storage.ReplayTagTable
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.set
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReplayTagTableTest {
    @Test
    fun testCurrentSampleAfterPlayback() = runTest {
        val context = storageContext("replay-current-sample")
        val directory = Files.createTempDirectory("replay-current-sample-test")

        val storage = context.request(ControlsStoragePlugin)
        val operations = NativeFileEnvelopeOperations(storage.io)
        val recordedTime = Instant.fromEpochSeconds(1_000)
        val playbackTime = Instant.fromEpochSeconds(2_000)
        val qualityName = "sensor" + TagState.TAG_QUALITY_SUFFIX
        val quality = Meta { set(TagState.quality, TagState.GOOD_QUALITY) }
        val headers = metaHeaders("sensor", qualityName)
        val rows = (0..1).map { index ->
            tagRow(recordedTime + index.milliseconds, "sensor" to Meta(index), qualityName to quality)
        }
        operations.writeRows("samples", directory, headers, rows, recordedTime, recordedTime + 1.milliseconds)
        val table = ReplayTagTable(TableStorageIndex(storage, directory), mapOf("sensor" to MetaDescriptor()))

        val row = async {
            table.messageFlow.filterIsInstance<PropertyChangedMessage>().first {
                it.time == playbackTime + 1.milliseconds
            }
        }
        table.play(recordedTime, recordedTime + 1.milliseconds, playbackTime, 1.0).join()
        val expected = ValueWithTime(Meta(1), playbackTime + 1.milliseconds)
        val state = table.subscribe("sensor")
        assertEquals(expected, state.valueWithTime)
        assertEquals(expected, state.subscribeWithTime().first())
        assertEquals(Meta(1), table.readTag("sensor"))
        assertEquals(mapOf("sensor" to Meta(1)), table.readAllValues())
        assertEquals(Meta(1), row.await().value)
        assertEquals(quality, table.readTagQuality("sensor").value)
        assertTimeSeriesHeaders(table, quality)

        context.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun testLifecycle() = runTest(timeout = 2.seconds) {
        val context = storageContext("replay-lifecycle")
        val directory = Files.createTempDirectory("replay-lifecycle-test")
        val index = TableStorageIndex(context.request(ControlsStoragePlugin), directory)
        val table = ReplayTagTable(index, emptyMap())
        assertEquals(LifecycleState.STOPPED, table.lifecycleState)

        val states = async(start = CoroutineStart.UNDISPATCHED) {
            table.messageFlow.filterIsInstance<DeviceLifeCycleMessage>().map { it.state }.take(3).toList()
        }

        table.start()
        assertEquals(LifecycleState.STARTED, table.lifecycleState)
        table.stop()

        assertEquals(LifecycleState.STOPPED, table.lifecycleState)
        assertEquals(listOf(LifecycleState.STARTING, LifecycleState.STARTED, LifecycleState.STOPPED), states.await())

        context.close()
        directory.toFile().deleteRecursively()
    }
}
