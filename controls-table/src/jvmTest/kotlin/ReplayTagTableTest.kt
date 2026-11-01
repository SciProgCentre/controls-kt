package space.kscience.controls.tagtable

import app.cash.turbine.test
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceLifeCycleMessage
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.storage.ReplayTagTable
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.controls.tagtable.timeseries.collectTable
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.set
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import space.kscience.tables.toColumnTable
import java.nio.file.Files
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReplayTagTableTest {
    @Test
    fun testCurrentSampleAfterPlayback() = runTest {
        val context = Context("replay-current-sample") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ControlsStoragePlugin)
        }
        val directory = Files.createTempDirectory("replay-current-sample-test")

        val storage = context.request(ControlsStoragePlugin)
        val operations = NativeFileEnvelopeOperations(storage.io)
        val recordedTime = Instant.fromEpochSeconds(1_000)
        val playbackTime = Instant.fromEpochSeconds(2_000)
        val qualityName = "sensor" + TagState.TAG_QUALITY_SUFFIX
        val quality = Meta { set(TagState.quality, TagState.GOOD_QUALITY) }
        val headers = listOf(
            TagTable.timeColumnHeader,
            SimpleColumnHeader("sensor", typeOf<Meta>(), Meta.EMPTY),
            SimpleColumnHeader(qualityName, typeOf<Meta>(), Meta.EMPTY),
        )
        val rows = (0..1).map { index ->
            MapRow(
                mapOf(
                    TagTable.timeColumnHeader.name to space.kscience.controls.tagtable.timeseries.Meta(
                        recordedTime + index.milliseconds
                    ),
                    "sensor" to Meta(index),
                    qualityName to quality,
                )
            )
        }
        val envelope = ZipRowsEnvelopeConverter.meta.writeRows(RowTable(headers, rows), Meta {
            set(RowEnvelopeMetaSpec.startTime, recordedTime)
            set(RowEnvelopeMetaSpec.endTime, recordedTime + 1.milliseconds)
        })
        operations.writeEnvelope("samples", directory, envelope)
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
        for (withQuality in listOf(false, true)) {
            val sampled = table.readTimeSeries(1.milliseconds, withQuality).collectTable(1)
            val expectedHeaders = listOf(TagTable.timeColumnHeader.name, "sensor") +
                    if (withQuality) listOf(qualityName) else emptyList()
            assertEquals(expectedHeaders, sampled.headers.map { it.name })
            val columns = sampled.toColumnTable()
            assertEquals(expectedHeaders, columns.headers.map { it.name })
            if (withQuality) {
                assertEquals(typeOf<Meta>(), sampled.headers.last().type)
                assertEquals(quality, columns.getOrNull(0, qualityName))
            }
        }

        context.close()
    }

    @Test
    fun testLifecycle() = runTest(timeout = 2.seconds) {
        val context = Context("replay-lifecycle") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(ControlsStoragePlugin)
        }
        val directory = Files.createTempDirectory("replay-lifecycle-test")
        val index = TableStorageIndex(context.request(ControlsStoragePlugin), directory)
        val table = ReplayTagTable(index, emptyMap())
        assertEquals(LifecycleState.STOPPED, table.lifecycleState)

        table.messageFlow.filterIsInstance<DeviceLifeCycleMessage>().map { it.state }.test {
            assertEquals(LifecycleState.STARTING, awaitItem())
            assertEquals(LifecycleState.STARTED, awaitItem())
            assertEquals(LifecycleState.STOPPED, awaitItem())
            cancel()
        }

        table.start()
        assertEquals(LifecycleState.STARTED, table.lifecycleState)
        table.stop()

        assertEquals(LifecycleState.STOPPED, table.lifecycleState)

        context.close()

        println(coroutineContext.job.children.filter { it.isActive })
    }
}
