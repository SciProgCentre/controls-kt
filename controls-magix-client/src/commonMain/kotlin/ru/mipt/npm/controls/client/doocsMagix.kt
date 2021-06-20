package ru.mipt.npm.controls.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import space.kscience.dataforge.meta.MetaItem


/*
  "action":"get|set",
  "eq_address": "string",
  "eq_data": {
    "type_id": "int[required]",
    "type": "string[optional]",
    "value": "object|value",
    "event_id": "int[optional]",
    "error": "int[optional]",
    "time": "long[optional]",
    "message": "string[optional]"
  }
 */

@Serializable
public enum class DoocsAction {
    get,
    set,
    names
}

@Serializable
public data class EqData(
    @SerialName("type_id")
    val typeId: Int,
    val type: String? = null,
    val value: MetaItem? = null,
    @SerialName("event_id")
    val eventId: Int? = null,
    val error: Int? = null,
    val time: Long? = null,
    val message: String? = null
) {
    public companion object {
        internal const val DATA_NULL: Int = 0
        internal const val DATA_INT: Int = 1
        internal const val DATA_FLOAT: Int = 2
        internal const val DATA_STRING: Int = 3
        internal const val DATA_BOOL: Int = 4
        internal const val DATA_STRING16: Int = 5
        internal const val DATA_DOUBLE: Int = 6
        internal const val DATA_TEXT: Int = 7
        internal const val DATA_TDS: Int = 12
        internal const val DATA_XY: Int = 13
        internal const val DATA_IIII: Int = 14
        internal const val DATA_IFFF: Int = 15
        internal const val DATA_USTR: Int = 16
        internal const val DATA_TTII: Int = 18
        internal const val DATA_SPECTRUM: Int = 19
        internal const val DATA_XML: Int = 20
        internal const val DATA_XYZS: Int = 21
        internal const val DATA_IMAGE: Int = 22
        internal const val DATA_GSPECTRUM: Int = 24
        internal const val DATA_SHORT: Int = 25
        internal const val DATA_LONG: Int = 26
        internal const val DATA_USHORT: Int = 27
        internal const val DATA_UINT: Int = 28
        internal const val DATA_ULONG: Int = 29


        internal const val DATA_A_FLOAT: Int = 100
        internal const val DATA_A_TDS: Int = 101
        internal const val DATA_A_XY: Int = 102
        internal const val DATA_A_USTR: Int = 103
        internal const val DATA_A_INT: Int = 105
        internal const val DATA_A_BYTE: Int = 106
        internal const val DATA_A_XYZS: Int = 108
        internal const val DATA_MDA_FLOAT: Int = 109
        internal const val DATA_A_DOUBLE: Int = 110
        internal const val DATA_A_BOOL: Int = 111
        internal const val DATA_A_STRING: Int = 112
        internal const val DATA_A_SHORT: Int = 113
        internal const val DATA_A_LONG: Int = 114
        internal const val DATA_MDA_DOUBLE: Int = 115
        internal const val DATA_A_USHORT: Int = 116
        internal const val DATA_A_UINT: Int = 117
        internal const val DATA_A_ULONG: Int = 118

        internal const val DATA_A_THUMBNAIL: Int = 120

        internal const val DATA_A_TS_BOOL: Int = 1000
        internal const val DATA_A_TS_INT: Int = 1001
        internal const val DATA_A_TS_FLOAT: Int = 1002
        internal const val DATA_A_TS_DOUBLE: Int = 1003
        internal const val DATA_A_TS_LONG: Int = 1004
        internal const val DATA_A_TS_STRING: Int = 1005
        internal const val DATA_A_TS_USTR: Int = 1006
        internal const val DATA_A_TS_XML: Int = 1007
        internal const val DATA_A_TS_XY: Int = 1008
        internal const val DATA_A_TS_IIII: Int = 1009
        internal const val DATA_A_TS_IFFF: Int = 1010
        internal const val DATA_A_TS_SPECTRUM: Int = 1013
        internal const val DATA_A_TS_XYZS: Int = 1014
        internal const val DATA_A_TS_GSPECTRUM: Int = 1015

        internal const val DATA_KEYVAL: Int = 1016

        internal const val DATA_A_TS_SHORT: Int = 1017
        internal const val DATA_A_TS_USHORT: Int = 1018
        internal const val DATA_A_TS_UINT: Int = 1019
        internal const val DATA_A_TS_ULONG: Int = 1020
    }
}

@Serializable
public data class DoocsPayload(
    val action: DoocsAction,
    @SerialName("eq_address")
    val address: String,
    @SerialName("eq_data")
    val data: EqData?
)