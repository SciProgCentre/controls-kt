package space.kscience.controls.storage

import space.kscience.dataforge.context.Global
import space.kscience.dataforge.io.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.tables.MapRow
import space.kscience.tables.RowTable
import space.kscience.tables.SimpleColumnHeader
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class RowsEnvelopeConverterTest {

    @Test
    fun testSerialization() {
        val columnsCount = 10
        val rowsCount = 10000

        val headers = (1..columnsCount).map { i ->
            SimpleColumnHeader<Double>("column_$i", typeOf<Double>(), Meta.EMPTY)
        }

        val random = Random(42)
        val data = List(rowsCount) {
            MapRow(headers.associate { header ->
                header.name to random.nextDouble()
            })
        }

        val table = RowTable(headers, data)

        val converter = ZipRowsEnvelopeConverter(MetaConverter.double, typeOf<Double>())

        val envelope = converter.writeRows(table)

        val envelopeFormat = TaggedEnvelopeFormat(Global.io)

        val binary = Binary {
            envelopeFormat.writeTo(this, envelope)
        }
        val bytes = binary.toByteArray()
        println(bytes.size)

        val readEnvelope = envelopeFormat.readFrom(bytes.asBinary())


        val resultTable = converter.readRows(readEnvelope)

        assertEquals(table.headers.size, resultTable.headers.size)
        table.headers.zip(resultTable.headers).forEach { (expected, actual) ->
            assertEquals(expected.name, actual.name)
        }

        val resultRows = resultTable.rowSequence().toList()
        assertEquals(rowsCount, resultRows.size)

        for (i in 0 until rowsCount) {
            val expectedRow = data[i]
            val actualRow = resultRows[i]
            headers.forEach { header ->
                assertEquals(
                    expectedRow.getOrNull(header.name),
                    actualRow.getOrNull(header.name),
                    "Error in row $i, column ${header.name}"
                )
            }
        }
    }
}