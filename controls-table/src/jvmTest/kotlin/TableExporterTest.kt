package space.kscience.controls.tagtable.export

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.TagState
import space.kscience.controls.tagtable.TagTable
import space.kscience.controls.tagtable.storage.RowEnvelopeMetaSpec
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import java.nio.file.Files
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import space.kscience.controls.tagtable.timeseries.Meta as timeMeta

class TableExporterTest {
    @Test
    fun exportMergesValuesAndAppliesMinimumRowDistance() = runTest {
        val context = Context("table-export-test") {
            plugin(ControlsStoragePlugin)
        }
        val directory = Files.createTempDirectory("table-export-test")
        val tagTableName = Name.of("plant")
        val deviceName = Name.of("pump")
        val baseTime = Instant.fromEpochSeconds(1_000)
        val storage = context.request(ControlsStoragePlugin)
        val operations = NativeFileEnvelopeOperations(storage.io)
        val qualityColumn = "sensor${TagState.TAG_QUALITY_SUFFIX}"
        val headers = listOf(
            TagTable.timeColumnHeader,
            SimpleColumnHeader("sensor", typeOf<Meta>(), Meta.EMPTY),
            SimpleColumnHeader(qualityColumn, typeOf<Meta>(), Meta.EMPTY),
        )
        val qualityValues = listOf(TagState.GOOD_QUALITY, TagState.BAD_QUALITY, TagState.GOOD_QUALITY)
        val tagRows = (1..3).map { value ->
            val second = value * 2 - 1
            MapRow(
                mapOf(
                    TagTable.timeColumnHeader.name to timeMeta(baseTime + second.seconds),
                    "sensor" to Meta(value),
                    qualityColumn to Meta { set(TagState.quality, qualityValues[value - 1]) },
                )
            )
        }
        val envelope = ZipRowsEnvelopeConverter.meta.writeRows(
            RowTable(headers, tagRows),
            Meta {
                set(RowEnvelopeMetaSpec.startTime, baseTime + 1.seconds)
                set(RowEnvelopeMetaSpec.endTime, baseTime + 5.seconds)
            },
        )
        operations.writeEnvelope("samples", directory, envelope)

        val index = TableStorageIndex(storage, directory)
        try {
            val messageStorage = InMemoryDeviceMessageStorage(
                listOf(
                    PropertyChangedMessage(baseTime, "pressure", Meta(10), deviceName),
                    PropertyChangedMessage(baseTime + 2.seconds, "pressure", Meta(20), deviceName),
                    PropertyChangedMessage(baseTime + 4.seconds, "pressure", Meta(30), deviceName),
                )
            )
            val exporter = TableExporter(mapOf(tagTableName to index), messageStorage)

            val exported = exporter.export(
                timeRange = baseTime..(baseTime + 5.seconds),
                minimalRowDistance = 2.seconds,
                columns = listOf(
                    TagExportColumn(tagTableName, "sensor"),
                    DevicePropertyExportColumn(deviceName, "pressure"),
                ),
            )

            val rows = exported.rowSequence().toList()
            assertEquals(
                listOf("time", "plant.sensor", "plant.sensor.@quality", "pump.pressure"),
                exported.headers.map { it.name },
            )
            assertEquals(
                listOf(
                    (baseTime + 1.seconds).toString(),
                    (baseTime + 3.seconds).toString(),
                    (baseTime + 5.seconds).toString(),
                ),
                rows.map { it.getOrNull("time")?.string ?: error("Missing exported time") },
            )
            assertEquals(listOf(Meta(1), Meta(2), Meta(3)), rows.map { it.getOrNull("plant.sensor") })
            assertEquals(
                listOf(TagState.GOOD_QUALITY, TagState.BAD_QUALITY, TagState.GOOD_QUALITY),
                rows.map { it.getOrNull("plant.sensor.@quality")?.get(TagState.quality) },
            )
            assertEquals(listOf(Meta(10), Meta(20), Meta(30)), rows.map { it.getOrNull("pump.pressure") })
        } finally {
            index.stop()
        }

        context.close()
    }
}

private class InMemoryDeviceMessageStorage(
    private val messages: List<DeviceMessage>,
) : DeviceMessageStorage {
    override suspend fun write(event: DeviceMessage) {
        error("Writing is not supported in this test storage")
    }

    override fun read(
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?,
    ): Flow<DeviceMessage> = messages.asFlow().filter { message ->
        (range == null || message.time in range) &&
                (sourceDevice == null || message.sourceDevice == sourceDevice) &&
                (targetDevice == null || message.targetDevice == targetDevice)
    }

    override fun read(
        eventType: String,
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?,
    ): Flow<DeviceMessage> = read(range, sourceDevice, targetDevice).filter {
        DeviceMessage.serialNameFor(it) == eventType
    }

    override fun close() = Unit
}