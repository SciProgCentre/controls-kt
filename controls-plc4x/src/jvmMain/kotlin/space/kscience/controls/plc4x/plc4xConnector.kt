package space.kscience.controls.plc4x

import org.apache.plc4x.java.api.types.PlcValueType
import org.apache.plc4x.java.api.value.PlcValue
import org.apache.plc4x.java.spi.values.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.asName
import java.math.BigInteger

internal fun PlcValue.toMeta(): Meta = Meta {
    when (plcValueType) {
        null, PlcValueType.NULL -> value = Null
        PlcValueType.BOOL -> value = this@toMeta.boolean.asValue()
        PlcValueType.BYTE -> this@toMeta.byte.asValue()
        PlcValueType.WORD -> this@toMeta.short.asValue()
        PlcValueType.DWORD -> this@toMeta.int.asValue()
        PlcValueType.LWORD -> this@toMeta.long.asValue()
        PlcValueType.USINT -> this@toMeta.short.asValue()
        PlcValueType.UINT -> this@toMeta.int.asValue()
        PlcValueType.UDINT -> this@toMeta.long.asValue()
        PlcValueType.ULINT -> this@toMeta.bigInteger.asValue()
        PlcValueType.SINT -> this@toMeta.byte.asValue()
        PlcValueType.INT -> this@toMeta.short.asValue()
        PlcValueType.DINT -> this@toMeta.int.asValue()
        PlcValueType.LINT -> this@toMeta.long.asValue()
        PlcValueType.REAL -> this@toMeta.float.asValue()
        PlcValueType.LREAL -> this@toMeta.double.asValue()
        PlcValueType.CHAR -> this@toMeta.int.asValue()
        PlcValueType.WCHAR -> this@toMeta.short.asValue()
        PlcValueType.STRING -> this@toMeta.string.asValue()
        PlcValueType.WSTRING -> this@toMeta.string.asValue()
        PlcValueType.TIME -> this@toMeta.duration.toString().asValue()
        PlcValueType.LTIME -> this@toMeta.duration.toString().asValue()
        PlcValueType.DATE -> this@toMeta.date.toString().asValue()
        PlcValueType.LDATE -> this@toMeta.date.toString().asValue()
        PlcValueType.TIME_OF_DAY -> this@toMeta.time.toString().asValue()
        PlcValueType.LTIME_OF_DAY -> this@toMeta.time.toString().asValue()
        PlcValueType.DATE_AND_TIME -> this@toMeta.dateTime.toString().asValue()
        PlcValueType.DATE_AND_LTIME -> this@toMeta.dateTime.toString().asValue()
        PlcValueType.LDATE_AND_TIME -> this@toMeta.dateTime.toString().asValue()
        PlcValueType.Struct -> this@toMeta.struct.forEach { (name, item) ->
            set(name, item.toMeta())
        }

        PlcValueType.List -> {
            val listOfMeta = this@toMeta.list.map { it.toMeta() }
            if (listOfMeta.all { it.items.isEmpty() }) {
                value = listOfMeta.map { it.value ?: Null }.asValue()
            } else {
                setIndexed("@list".asName(), list.map { it.toMeta() })
            }
        }

        PlcValueType.RAW_BYTE_ARRAY -> this@toMeta.raw.asValue()
    }
}

private fun Value.toPlcValue(): PlcValue = when (type) {
    ValueType.NUMBER -> when (val number = number) {
        is Short -> PlcINT(number.toShort())
        is Int -> PlcDINT(number.toInt())
        is Long -> PlcLINT(number.toLong())
        is Float -> PlcREAL(number.toFloat())
        else -> PlcLREAL(number.toDouble())
    }

    ValueType.STRING -> PlcSTRING(string)
    ValueType.BOOLEAN -> PlcBOOL(boolean)
    ValueType.NULL -> PlcNull()
    ValueType.LIST -> TODO()
}

internal fun Meta.toPlcValue(hint: PlcValueType): PlcValue = when (hint) {
    PlcValueType.Struct -> PlcStruct(
        items.entries.associate { (token, item) ->
            token.toString() to item.toPlcValue(PlcValueType.Struct)
        }
    )

    PlcValueType.NULL -> PlcNull()
    PlcValueType.BOOL -> PlcBOOL(boolean)
    PlcValueType.BYTE -> PlcBYTE(int)
    PlcValueType.WORD -> PlcWORD(int)
    PlcValueType.DWORD -> PlcDWORD(int)
    PlcValueType.LWORD -> PlcLWORD(long)
    PlcValueType.USINT -> PlcLWORD(short)
    PlcValueType.UINT -> PlcUINT(int)
    PlcValueType.UDINT -> PlcDINT(long)
    PlcValueType.ULINT -> (number as? BigInteger)?.let { PlcULINT(it) } ?: PlcULINT(long)
    PlcValueType.SINT -> PlcSINT(int)
    PlcValueType.INT -> PlcINT(int)
    PlcValueType.DINT -> PlcDINT(int)
    PlcValueType.LINT -> PlcLINT(long)
    PlcValueType.REAL -> PlcREAL(float)
    PlcValueType.LREAL -> PlcLREAL(double)
    PlcValueType.CHAR -> PlcCHAR(int)
    PlcValueType.WCHAR -> PlcWCHAR(short)
    PlcValueType.STRING -> PlcSTRING(string)
    PlcValueType.WSTRING -> PlcWSTRING(string)
    PlcValueType.TIME -> PlcTIME(string?.let { java.time.Duration.parse(it) })
    PlcValueType.LTIME -> PlcLTIME(string?.let { java.time.Duration.parse(it) })
    PlcValueType.DATE -> PlcDATE(string?.let { java.time.LocalDate.parse(it) })
    PlcValueType.LDATE -> PlcLDATE(string?.let { java.time.LocalDate.parse(it) })
    PlcValueType.TIME_OF_DAY -> PlcTIME_OF_DAY(string?.let { java.time.LocalTime.parse(it) })
    PlcValueType.LTIME_OF_DAY -> PlcLTIME_OF_DAY(string?.let { java.time.LocalTime.parse(it) })
    PlcValueType.DATE_AND_TIME -> PlcDATE_AND_TIME(string?.let { java.time.LocalDateTime.parse(it) })
    PlcValueType.DATE_AND_LTIME -> PlcDATE_AND_LTIME(string?.let { java.time.LocalDateTime.parse(it) })
    PlcValueType.LDATE_AND_TIME -> PlcLDATE_AND_TIME(string?.let { java.time.LocalDateTime.parse(it) })
    PlcValueType.List -> PlcList().apply {
        value?.list?.forEach { add(it.toPlcValue()) }
        getIndexed("@list").forEach { (_, meta) ->
            if (meta.items.isEmpty()) {
                meta.value?.let { add(it.toPlcValue()) }
            } else {
                add(meta.toPlcValue(PlcValueType.Struct))
            }
        }
    }

    PlcValueType.RAW_BYTE_ARRAY -> PlcRawByteArray(
        value?.list?.map { it.number.toByte() }?.toByteArray() ?: error("The meta content is not byte array")
    )
}