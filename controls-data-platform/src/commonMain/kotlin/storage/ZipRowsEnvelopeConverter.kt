package space.kscience.controls.dataplatform.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import space.kscience.attributes.SafeType
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.asBinary
import space.kscience.dataforge.io.toByteArray
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.getIndexedList
import space.kscience.dataforge.names.parseAsName
import space.kscience.tables.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * A converter implementation for transforming rows of data into a compressed envelope format
 * and vice versa, using serialization and meta-conversion tools.
 *
 * @param T The type of the data in the rows.
 * @property converter A metadata-based converter used for serializing and deserializing individual cell values.
 * @property type The safe type information associated with the data type of the rows.
 */
public class ZipRowsEnvelopeConverter<T>(
    public val converter: MetaConverter<T>,
    public val type: SafeType<T>
) : RowsEnvelopeConverter<T> {


    @OptIn(ExperimentalSerializationApi::class)
    override fun writeRows(rows: Rows<T>, meta: Meta): Envelope {
        val headerMeta = Meta {
            rows.headers.forEach { header ->
                append("column", Meta {
                    "name" put header.name
                    header.meta.takeIf { !it.isEmpty() }.let {
                        "meta" put header.meta
                    }
                })
            }
        }
        val meta = Meta {
            "@header" put headerMeta
            "format" put {
                "description" put """A Json array of objects representing rows, compressed with ZIP/DEFLATE."""
                "type" put "rows.meta.zip"
            }
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

    @OptIn(ExperimentalSerializationApi::class)
    override fun readRows(envelope: Envelope): Rows<T> {
        val header: TableHeader<T> = envelope.meta.getIndexedList("@header.column".parseAsName()).map { item ->
            SimpleColumnHeader(item["name"].string ?: "default", type.kType, item["meta"] ?: Meta.EMPTY)
        }
        val bais = ByteArrayInputStream(envelope.data?.toByteArray() ?: error("No data in envelope"))
        val zipInputStream = InflaterInputStream(bais)
        val dao = Json.decodeFromStream<List<Map<String, Meta>>>(zipInputStream)
        zipInputStream.close()
        val rows = dao.map { m ->
            MapRow(m.mapValues { converter.read(it.value) })
        }

        return RowTable(header, rows)
    }

}