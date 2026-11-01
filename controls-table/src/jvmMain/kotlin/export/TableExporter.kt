package space.kscience.controls.tagtable.export

import space.kscience.controls.storage.DeviceMessageStorage
import space.kscience.controls.tagtable.storage.Replay
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.tables.Table
import kotlin.time.Duration
import kotlin.time.Instant


public sealed interface ExportColumn

public data class DevicePropertyExportColumn(
    val device: Name,
    val property: String,
): ExportColumn

public data class TagExportColumn(
    val tagTable: Name,
    val tag: String,
)


public class TableExporter(
    private val replay: Replay,
    private val messageStorage: DeviceMessageStorage
) {

    public suspend fun export(
        timeRange: ClosedRange<Instant>,
        minimalRowDistance: Duration = Duration.ZERO,
        columns: List<ExportColumn>
    ): Table<Meta>{
        TODO()
    }
}