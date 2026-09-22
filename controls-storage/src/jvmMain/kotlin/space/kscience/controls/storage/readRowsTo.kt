package space.kscience.controls.storage

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.dataType
import space.kscience.dataforge.io.toByteArray
import space.kscience.dataforge.meta.Meta
import space.kscience.tables.Row
import space.kscience.tables.TableHeader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.zip.InflaterInputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** The inflated body exceeded [maxInflatedBytes]. */
public class InflatedByteLimitExceededException(public val maxInflatedBytes: Long) :
    IOException("Inflated data exceeds maxInflatedBytes=$maxInflatedBytes")

/**
 * Read one envelope, passing its header to [onHeader] and each row to [action] in stored order.
 * The positive [maxInflatedBytes] limit applies to the inflated body, including JSON whitespace.
 * A body ending exactly at the limit is accepted; an extra byte throws [InflatedByteLimitExceededException].
 *
 * Returns the inflated byte count only after the full JSON array and its trailing content have been checked.
 * The header is delivered even for an empty array. Callback effects are not rolled back on later failure.
 * This function owns and closes its streams, but runs blocking work on the caller's dispatcher.
 * Cancellation is checked between reads and callbacks; it cannot interrupt an ongoing blocking call.
 * The byte limit does not bound memory or execution time.
 */
@OptIn(ExperimentalSerializationApi::class)
public suspend fun <T> ZipRowsEnvelopeConverter<T>.readRowsTo(
    envelope: Envelope,
    maxInflatedBytes: Long,
    onHeader: (TableHeader<T>) -> Unit = {},
    action: (Row<T>) -> Unit,
): Long {
    require(maxInflatedBytes > 0) { "maxInflatedBytes must be positive" }
    require(envelope.dataType == envelopeType) { "Envelope data type should be $envelopeType" }
    val context = currentCoroutineContext()
    context.ensureActive()
    val bais = ByteArrayInputStream(envelope.data?.toByteArray() ?: error("No data in envelope"))
    context.ensureActive()
    return InflatedByteLimitInputStream(InflaterInputStream(bais), maxInflatedBytes, context).use { input ->
        val header = readHeader(envelope.meta)
        context.ensureActive()
        onHeader(header)
        context.ensureActive()
        for (row in Json.decodeToSequence<Map<String, Meta>>(input, DecodeSequenceMode.ARRAY_WRAPPED)) {
            context.ensureActive()
            val converted = readRow(row)
            context.ensureActive()
            action(converted)
            context.ensureActive()
        }
        context.ensureActive()
        input.bytesRead
    }
}

internal class InflatedByteLimitInputStream(
    private val input: InputStream,
    private val maxInflatedBytes: Long,
    private val context: CoroutineContext = EmptyCoroutineContext,
) : InputStream() {
    private var remaining = maxInflatedBytes

    val bytesRead: Long get() = maxInflatedBytes - remaining

    private fun requireEnd() {
        if (input.read() != -1) throw InflatedByteLimitExceededException(maxInflatedBytes)
    }

    override fun read(): Int {
        context.ensureActive()
        if (remaining == 0L) {
            requireEnd()
            return -1
        }
        return input.read().also { if (it >= 0) remaining-- }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        Objects.checkFromIndexSize(offset, length, buffer.size)
        if (length == 0) return 0
        context.ensureActive()
        if (remaining == 0L) {
            requireEnd()
            return -1
        }
        return input.read(buffer, offset, minOf(length.toLong(), remaining).toInt()).also {
            if (it > 0) remaining -= it
        }
    }

    override fun close() = input.close()
}
