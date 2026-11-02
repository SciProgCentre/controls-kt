package space.kscience.controls.tagtable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.tagtable.export.DevicePropertyExportColumn
import space.kscience.controls.tagtable.export.TableExporter
import space.kscience.controls.tagtable.export.TagExportColumn
import space.kscience.controls.tagtable.export.sourceStateExportColumns
import space.kscience.controls.tagtable.storage.TableStorageIndex
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TableExporterTest {
    /* LLM generated code: Verifies source-state columns include OPC time offset only for OPC sources. */
    @Test
    fun sourceStateColumnsIncludeOpcTimeOffsetOnlyForOpcSources() {
        val configuration = TagTableConfiguration(
            sources = mapOf(
                "opc" to OpcTagSourceConfig("localhost"),
                "modbus" to ModbusTcpTagSourceConfig("localhost"),
            ),
            timers = emptyMap(),
            tags = emptyMap(),
        )

        assertEquals(
            listOf(
                "source.opc/state/lifeCycle",
                "source.opc/state/sourceTimeOffset",
                "source.modbus/state/lifeCycle",
            ),
            configuration.sourceStateExportColumns().map { it.exportColumnName },
        )
    }

    @Test
    fun exportMergesValuesAndAppliesMinimumRowDistance() = runTest {
        val context = storageContext("table-export-test")
        val directory = Files.createTempDirectory("table-export-test")
        val deviceName = Name.of("pump")
        val baseTime = Instant.fromEpochSeconds(1_000)
        val storage = context.request(ControlsStoragePlugin)
        val operations = NativeFileEnvelopeOperations(storage.io)
        val qualityColumn = "sensor${TagState.TAG_QUALITY_SUFFIX}"
        val headers = metaHeaders("sensor", qualityColumn)
        val qualityValues = listOf(TagState.GOOD_QUALITY, TagState.BAD_QUALITY, TagState.GOOD_QUALITY)
        val tagRows = (1..3).map { value ->
            val second = value * 2 - 1
            tagRow(
                baseTime + second.seconds,
                "sensor" to Meta(value),
                qualityColumn to Meta { set(TagState.quality, qualityValues[value - 1]) },
            )
        }
        operations.writeRows("samples", directory, headers, tagRows, baseTime + 1.seconds, baseTime + 5.seconds)

        val index = TableStorageIndex(storage, directory)
        val messageStorage = InMemoryDeviceMessageStorage(
            listOf(
                PropertyChangedMessage(baseTime, "pressure", Meta(10), deviceName),
                PropertyChangedMessage(baseTime + 2.seconds, "pressure", Meta(20), deviceName),
                PropertyChangedMessage(baseTime + 4.seconds, "pressure", Meta(30), deviceName),
            )
        )
        val exporter = TableExporter(index, messageStorage)

        val exported = exporter.export(
            timeRange = baseTime..(baseTime + 5.seconds),
            minimalRowDistance = 2.seconds,
            columns = listOf(
                TagExportColumn("sensor", exportColumnName = "sensor-value"),
                TagExportColumn("sensor.@quality"),
                DevicePropertyExportColumn(deviceName, "pressure"),
            ),
        )

        val rows = exported.rowSequence().toList()
        assertEquals(
            listOf("time", "sensor-value", "sensor.@quality", "pump/pressure"),
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
        assertEquals(listOf(Meta(1), Meta(2), Meta(3)), rows.map { it.getOrNull("sensor-value") })
        assertEquals(
            listOf(TagState.GOOD_QUALITY, TagState.BAD_QUALITY, TagState.GOOD_QUALITY),
            rows.map { it.getOrNull("sensor.@quality")?.get(TagState.quality) },
        )
        assertEquals(listOf(Meta(10), Meta(20), Meta(30)), rows.map { it.getOrNull("pump/pressure") })

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