package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.binary.FrameProducer
import space.kscience.controls.dataplatform.DataPlatform
import space.kscience.controls.dataplatform.timeseries.TimeSeriesValues
import space.kscience.controls.dataplatform.timeseries.toRow
import space.kscience.controls.time.ValueWithTime
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.meta.Meta
import space.kscience.tables.RowTable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Streams binary data as a flow of envelopes by periodically collecting rows of data from the platform.
 *
 * If [maxPause] is not null, envelopes are automatically collected when [maxPause] time passes since the last row.
 *
 * @param readInterval the interval between the generation of each row.
 * @param converter the converter used to transform rows into envelopes.
 * @param maxRows the number of rows to include in each envelope, default is 10000.
 * @param maxDuration the maximum duration of a single envelope collection, default is 3 hours.
 * @param maxPause the maximum delay between rows for them to be put in the same envelope, default is null.
 * @param compression optional compression settings for rows.
 * @return a flow of envelopes generated from the rows.
 */
public fun DataPlatform.flowBinaryData(
    readInterval: Duration,
    converter: RowsEnvelopeConverter<Meta>,
    maxRows: Int = 10000,
    maxDuration: Duration = 3.hours,
    maxPause: Duration? = null,
    compression: RowsCompression? = null,
): Flow<Envelope> {
    val rows = if (compression == null) {
        readTimeSeries(readInterval)
    } else {
        readTimeSeries(readInterval).compress(compression)
    }

    return channelFlow {
        val rowBuffer = mutableListOf<TimeSeriesValues<Meta>>()
        var lastCollectionTime: Instant = clock.now()
        var lastRowTime: Instant? = null
        val mutex = Mutex()

        suspend fun collect() {
            //ignore if rows are empty
            if (rowBuffer.isEmpty()) return
            val now = clock.now()

            val table = RowTable(rows.headers, rowBuffer.map { it.toRow() })
            val envelope = converter.writeRows(
                rows = table,
                meta = Meta {
                    "time" put now.toString()
                    "startTime" put rowBuffer.first().time.toString()
                    "endTime" put rowBuffer.last().time.toString()
                    "numberOfRows" put rowBuffer.size
                    "readInterval" put readInterval.toString()
                    "maxRows" put maxRows
                    "maxDuration" put maxDuration.toString()
                    compression?.let { "timeSerriesCompression" put compression.toMeta() }
                }
            )
            send(envelope)
            rowBuffer.clear()

            // put a line with all values at the beginning of each block to avoid having unknown start values in binary blocks
            if (compression != null) {
                rowBuffer.add(ValueWithTime(readValues(), now))
            }

            lastCollectionTime = now
        }

        //collect envelope if more than [maxDelay] time passed since last row
        if (maxPause != null) {
            launch {
                delay(maxPause)
                lastRowTime?.let {
                    if (clock.now() - it > maxPause) mutex.withLock { collect() }
                }
            }
        }

        rows.subscribe().collect {
            mutex.withLock {
                val now = clock.now()
                // if max duration is exceeded, collect and then add
                if (now - lastCollectionTime > maxDuration) collect()
                rowBuffer.add(it)
                lastRowTime = now
                if (rowBuffer.size >= maxRows) collect()
            }
        }
    }
}

public fun DataPlatform.asFrameProducer(
    readInterval: Duration,
    converter: RowsEnvelopeConverter<Meta>,
    maxRows: Int = 10000,
    maxDuration: Duration = 3.hours,
    maxPause: Duration? = null,
    compression: RowsCompression? = null,
): FrameProducer {
    val flow = flowBinaryData(
        readInterval = readInterval,
        converter = converter,
        maxRows = maxRows,
        maxDuration = maxDuration,
        maxPause = maxPause,
        compression = compression
    ).shareIn(context, SharingStarted.Eagerly)

    return FrameProducer { flow }
}