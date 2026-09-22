package space.kscience.controls.storage

import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.dataType
import space.kscience.dataforge.io.toByteArray
import space.kscience.tables.Rows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.zip.InflaterInputStream

/** The inflated body exceeded [maxInflatedBytes]. */
public class InflatedByteLimitExceededException(public val maxInflatedBytes: Long) :
    IOException("Inflated data exceeds maxInflatedBytes=$maxInflatedBytes")

/**
 * Read one envelope with a positive [maxInflatedBytes] limit on its inflated body.
 * A valid body ending exactly at the limit is accepted. An extra byte throws
 * [InflatedByteLimitExceededException] without returning partial rows.
 * This limit does not bound the materialized table's memory.
 */
public fun <T> ZipRowsEnvelopeConverter<T>.readRowsWithInflatedByteLimit(
    envelope: Envelope,
    maxInflatedBytes: Long,
): Rows<T> {
    require(maxInflatedBytes > 0) { "maxInflatedBytes must be positive" }
    require(envelope.dataType == envelopeType) { "Envelope data type should be $envelopeType" }
    val bais = ByteArrayInputStream(envelope.data?.toByteArray() ?: error("No data in envelope"))
    return InflatedByteLimitInputStream(InflaterInputStream(bais), maxInflatedBytes).use {
        readInflatedRows(envelope.meta, it)
    }
}

internal class InflatedByteLimitInputStream(
    private val input: InputStream,
    private val maxInflatedBytes: Long,
) : InputStream() {
    private var remaining = maxInflatedBytes

    private fun requireEnd() {
        if (input.read() != -1) throw InflatedByteLimitExceededException(maxInflatedBytes)
    }

    override fun read(): Int {
        if (remaining == 0L) {
            requireEnd()
            return -1
        }
        return input.read().also { if (it >= 0) remaining-- }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        Objects.checkFromIndexSize(offset, length, buffer.size)
        if (length == 0) return 0
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
