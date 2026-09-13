package space.kscience.controls.tagtable

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.storage.ReplayTagTable
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
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
import java.nio.file.Files
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class ReplayTagTableTest {
    @Test
    fun testCurrentSampleAfterPlayback() = runTest {
        val context = Context("replay-current-sample") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(ControlsStoragePlugin)
        }
        val directory = Files.createTempDirectory("replay-current-sample-test")
        try {
            val storage = context.request(ControlsStoragePlugin)
            val operations = NativeFileEnvelopeOperations(storage.io)
            val recordedTime = Instant.fromEpochSeconds(1_000)
            val playbackTime = Instant.fromEpochSeconds(2_000)
            val headers = listOf(
                TagTable.timeColumnHeader,
                SimpleColumnHeader("sensor", typeOf<Meta>(), Meta.EMPTY),
            )
            val rows = (0..1).map { index ->
                MapRow(mapOf(
                    TagTable.timeColumnHeader.name to space.kscience.controls.tagtable.timeseries.Meta(
                        recordedTime + index.milliseconds
                    ),
                    "sensor" to Meta(index),
                ))
            }
            val envelope = ZipRowsEnvelopeConverter.meta.writeRows(RowTable(headers, rows), Meta {
                set(RowEnvelopeMetaSpec.startTime, recordedTime)
                set(RowEnvelopeMetaSpec.endTime, recordedTime + 1.milliseconds)
            })
            operations.writeEnvelope("samples", directory, envelope)
            val table = ReplayTagTable(TableStorageIndex(storage, directory), mapOf("sensor" to MetaDescriptor()))
            try {
                val row = async(start = CoroutineStart.UNDISPATCHED) {
                    table.messageFlow.filterIsInstance<PropertyChangedMessage>().first {
                        it.property == TagTable.ROW_PROPERTY_NAME && it.time == playbackTime + 1.milliseconds
                    }
                }
                table.play(recordedTime, recordedTime + 1.milliseconds, playbackTime, 1.0).join()
                val expected = ValueWithTime(Meta(1), playbackTime + 1.milliseconds)
                val state = table.valueState("sensor")
                assertEquals(expected, state.valueWithTime)
                assertEquals(expected, state.subscribeWithTime().first())
                assertEquals(Meta(1), table.read("sensor"))
                assertEquals(mapOf("sensor" to Meta(1)), table.readAll())
                assertEquals(Meta { set("sensor", Meta(1)) }, row.await().value)
            } finally {
                table.stop()
            }
        } finally {
            context.cancel()
            context.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun testLifecycle() = runTest {
        val context = Context("replay-lifecycle") {
            plugin(IOPlugin)
            plugin(ControlsStoragePlugin)
        }
        val directory = Files.createTempDirectory("replay-lifecycle-test")
        try {
            val index = TableStorageIndex(context.request(ControlsStoragePlugin), directory)
            val table = ReplayTagTable(index, emptyMap())
            assertEquals(LifecycleState.STOPPED, table.lifecycleState)

            val states = async(start = CoroutineStart.UNDISPATCHED) {
                table.messageFlow.filterIsInstance<DeviceLifeCycleMessage>()
                    .map { it.state }.take(3).toList()
            }

            try {
                table.start()
                assertEquals(LifecycleState.STARTED, table.lifecycleState)
            } finally {
                table.stop()
            }
            assertEquals(LifecycleState.STOPPED, table.lifecycleState)
            assertEquals(
                listOf(LifecycleState.STARTING, LifecycleState.STARTED, LifecycleState.STOPPED),
                states.await(),
            )
        } finally {
            context.close()
            directory.toFile().deleteRecursively()
        }
    }
}
