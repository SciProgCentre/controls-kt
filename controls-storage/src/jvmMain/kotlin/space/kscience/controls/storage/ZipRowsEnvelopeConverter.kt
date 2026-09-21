package space.kscience.controls.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.asBinary
import space.kscience.dataforge.io.dataType
import space.kscience.dataforge.io.toByteArray
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.misc.DfType
import space.kscience.dataforge.names.getIndexedList
import space.kscience.dataforge.names.parseAsName
import space.kscience.tables.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A converter implementation for transforming rows of data into a compressed envelope format
 * and vice versa, using serialization and meta-conversion tools.
 *
 * @param T The type of the data in the rows.
 * @property converter A metadata-based converter used for serializing and deserializing individual cell values.
 * @property type The safe type information associated with the data type of the rows.
 */
@DfType(ZipRowsEnvelopeConverter.TYPE)
@OptIn(ExperimentalSerializationApi::class)
public class ZipRowsEnvelopeConverter<T>(
    public val converter: MetaConverter<T>,
    public val type: KType
) : RowsEnvelopeConverter<T> {

    override val envelopeType: String get() = ENVELOPE_TYPE

    override fun writeRows(rows: Rows<T>, meta: Meta): Envelope {
        val headerMeta = rows.headers.toMeta()
        val meta = Meta {
            "@header" put headerMeta
            Envelope.ENVELOPE_DESCRIPTION_KEY put """A Json array of objects representing rows, compressed with ZIP/DEFLATE."""
            Envelope.ENVELOPE_DATA_TYPE_KEY put envelopeType
            update(meta)
        }

        val rowsPrepared = rows.rowSequence().map { row ->
            val map = if (row is MapRow) row.values else rows.headers.associate { it.name to row.getOrNull(it.name) }
            map.mapValues { it.value?.let { value -> converter.convert(value) } ?: Meta.EMPTY }
        }.toList()

        val baos = ByteArrayOutputStream()

        val zipOutputStream = DeflaterOutputStream(baos)
        Json.encodeToStream(rowsPrepared, zipOutputStream)
        zipOutputStream.finish()
        return Envelope(meta, baos.toByteArray().asBinary())
    }

    override fun readRows(envelope: Envelope): Rows<T> = decodeRows(envelope, null)

    /**
     * Read one envelope with a positive [maxInflatedBytes] limit on its inflated body.
     * Exceeding the limit fails the read; the materialized table's memory is not bounded by it.
     */
    public fun readRows(envelope: Envelope, maxInflatedBytes: Long): Rows<T> {
        require(maxInflatedBytes > 0) { "maxInflatedBytes must be positive" }
        return decodeRows(envelope, maxInflatedBytes)
    }

    private fun decodeRows(envelope: Envelope, maxInflatedBytes: Long?): Rows<T> {
        require(envelope.dataType == envelopeType) { "Envelope data type should be $envelopeType" }

        val header: TableHeader<T> = envelope.meta.getIndexedList("@header.column".parseAsName()).map { item ->
            SimpleColumnHeader(item["name"].string ?: "default", type, item["meta"] ?: Meta.EMPTY)
        }
        val bais = ByteArrayInputStream(envelope.data?.toByteArray() ?: error("No data in envelope"))
        val inflater = InflaterInputStream(bais)
        val input = if (maxInflatedBytes == null) inflater else InflatedByteLimitInputStream(inflater, maxInflatedBytes)
        val dao = input.use { Json.decodeFromStream<List<Map<String, Meta>>>(it) }
        val rows = dao.map { m ->
            MapRow(m.mapValues { converter.read(it.value) })
        }

        return RowTable(header, rows)
    }

    public companion object {

        public const val ENVELOPE_TYPE: String = "rows.meta.zip"
        public const val TYPE: String = "envelope.${ENVELOPE_TYPE}"

        public val meta: ZipRowsEnvelopeConverter<Meta> = ZipRowsEnvelopeConverter(MetaConverter.meta, typeOf<Meta>())
    }

}

internal class InflatedByteLimitInputStream(
    private val input: InputStream,
    private val maxInflatedBytes: Long,
) : InputStream() {
    private var remaining = maxInflatedBytes

    private fun requireEnd() {
        if (input.read() != -1) throw IOException("Inflated data exceeds maxInflatedBytes=$maxInflatedBytes")
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
