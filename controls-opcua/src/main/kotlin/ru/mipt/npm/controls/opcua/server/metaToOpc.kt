package ru.mipt.npm.controls.opcua.server

import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.isLeaf
import space.kscience.dataforge.values.*
import java.time.Instant

/**
 * Convert Meta to OPC data value using
 */
internal fun Meta.toOpc(
    statusCode: StatusCode = StatusCode.GOOD,
    sourceTime: DateTime? = null,
    serverTime: DateTime? = null
): DataValue {
    val variant: Variant = if (isLeaf) {
        when (value?.type) {
            null, ValueType.NULL -> Variant.NULL_VALUE
            ValueType.NUMBER -> Variant(value!!.number)
            ValueType.STRING -> Variant(value!!.string)
            ValueType.BOOLEAN -> Variant(value!!.boolean)
            ValueType.LIST -> if (value!!.list.all { it.type == ValueType.NUMBER }) {
                Variant(value!!.doubleArray.toTypedArray())
            } else {
                Variant(value!!.stringList.toTypedArray())
            }
        }
    } else {
        Variant(Json.encodeToString(MetaSerializer,this))
    }
    return DataValue(variant, statusCode, sourceTime,serverTime ?: DateTime(Instant.now()))
}