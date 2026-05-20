package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.io.asSink
import kotlinx.io.buffered
import space.kscience.attributes.safeTypeOf
import space.kscience.controls.dataplatform.DataPlatform
import space.kscience.controls.time.clock
import space.kscience.dataforge.io.EnvelopeFormat
import space.kscience.dataforge.io.TaggedEnvelopeFormat
import space.kscience.dataforge.meta.MetaConverter
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
 * @param envelopeFormat the format used for encoding the data envelope. Defaults to `TaggedEnvelopeFormat`.
 *
 * @return a [Job] representing the lifecycle of the data collection and storage process. This job can be canceled to stop the operation.
 */
public fun DataPlatform.launchStorageProcess(
    directory: Path,
    readInterval: Duration,
    maxRowsPerEnvelope: Int = 10000,
    maxDuration: Duration = 3.hours,
    maxPause: Duration? = null,
    compression: RowsCompression? = null,
    clock: Clock = context.clock,
    envelopeFormat: EnvelopeFormat = TaggedEnvelopeFormat,
): Job = flowBinaryData(
    readInterval = readInterval,
    converter = ZipRowsEnvelopeConverter(MetaConverter.meta, safeTypeOf()),
    maxRows = maxRowsPerEnvelope,
    maxDuration = maxDuration,
    maxPause = maxPause,
    compression = compression,
).onEach { envelope ->
    val filePath = directory.resolve("dataPlarform_${clock.now().toString().replace(":", "-")}.df")
    filePath.outputStream().use {
        envelopeFormat.writeTo(it.asSink().buffered(), envelope)
    }
}.launchIn(this)
