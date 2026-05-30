package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import space.kscience.attributes.safeTypeOf
import space.kscience.controls.dataplatform.DataPlatform
import space.kscience.controls.instant
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.time.clock
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Directory splitting strategy for data storage.
 */
public sealed interface DataPlatformFileSplit {
    /**
     * Resolve a relative path to the directory where the data should be stored.
     */
    public fun resolveDirectory(envelope: Envelope, time: Instant): Path

    public data object Flat : DataPlatformFileSplit {
        override fun resolveDirectory(envelope: Envelope, time: Instant): Path = Path("")
    }

    public data class ByDate(val timeZone: TimeZone = TimeZone.currentSystemDefault()) : DataPlatformFileSplit {
        override fun resolveDirectory(envelope: Envelope, time: Instant): Path {
            val date = time.toLocalDateTime(timeZone)
            return Path(date.year.toString(), date.month.number.toString(), date.day.toString())
        }
    }

    public data class ByHour(val timeZone: TimeZone = TimeZone.currentSystemDefault()) : DataPlatformFileSplit {
        override fun resolveDirectory(envelope: Envelope, time: Instant): Path {
            val date = time.toLocalDateTime(timeZone)
            return Path(date.year.toString(), date.month.number.toString(), date.day.toString(), date.hour.toString())
        }
    }
}

/**
 * Stores data from the `DataPlatformDevice` into a file in the specified directory,
 * using the provided configurations such as interval, maximum rows per envelope,
 * maximum duration, and optional compression.
 *
 * @param directory the filesystem path where the data file will be written.
 * @param readInterval the interval at which data will be collected from the data platform.
 * @param maxRowsPerEnvelope the maximum number of rows to include in each data envelope. Default is 10,000.
 * @param maxDuration the maximum time duration for which data is collected into a single envelope. Default is 3 hours.
 * @param maxPause the maximum pause duration between data collection intervals. If null, no pause is enforced.
 * @param compression configuration for compressing the rows in the envelope. If null, no compression is used.
 * @param clock the clock instance used to timestamp the collected data. Defaults to the device's default clock.
 * @param strategy the naming strategy for organizing data files within the directory. Defaults to `DataPlatformStorageNamingStrategy.ByDate()`.
 * @param operations the file operations instance used to write the data envelope to a file. Defaults to `NativeFileEnvelopeOperations(context.io)`.
 *
 * @return a [Job] representing the lifecycle of the data collection and storage process. This job can be canceled to stop the operation.
 */
public fun DataPlatform.storeData(
    directory: Path,
    readInterval: Duration,
    maxRowsPerEnvelope: Int = 10000,
    maxDuration: Duration = 3.hours,
    maxPause: Duration? = null,
    compression: RowsCompression? = null,
    operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(context.io),
    strategy: DataPlatformFileSplit = DataPlatformFileSplit.ByDate(),
    clock: Clock = context.clock,
): Job = flowBinaryData(
    readInterval = readInterval,
    converter = ZipRowsEnvelopeConverter(MetaConverter.meta, safeTypeOf()),
    maxRows = maxRowsPerEnvelope,
    maxDuration = maxDuration,
    maxPause = maxPause,
    compression = compression,
).onEach { envelope ->
    val time = envelope.meta["@envelope.time"]?.instant ?: clock.now()
    val relativePath = strategy.resolveDirectory(envelope, time)
    val fileName = "data_${clock.now().toString().replace(":", "-")}"
    operations.writeEnvelope(fileName, directory.resolve(relativePath), envelope)
}.launchIn(this)
