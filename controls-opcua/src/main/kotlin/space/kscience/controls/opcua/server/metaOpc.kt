package space.kscience.controls.opcua.server

import kotlinx.serialization.json.Json
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext
import org.eclipse.milo.opcua.stack.core.types.builtin.*
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort
import space.kscience.controls.toMeta
import space.kscience.dataforge.meta.*
import java.time.Instant
import java.util.*
import kotlin.time.toKotlinInstant

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

/**
 * Convert OPC data value to Meta
 */
public fun Meta.Companion.fromOpc(value: Any?): Meta = when (value) {
    null -> Meta(Null)
    is Variant -> fromOpc(value.value)
    is Meta -> value
    is Value -> Meta(value)
    is Number -> when (value) {
        is UByte -> Meta(value.toShort().asValue())
        is UShort -> Meta(value.toInt().asValue())
        is UInteger -> Meta(value.toLong().asValue())
        is ULong -> Meta(value.toBigInteger().asValue())
        else -> Meta(value.asValue())
    }
    is Boolean -> Meta(value.asValue())
    is String -> Meta(value.asValue())
    is Char -> Meta(value.toString().asValue())
    is DateTime -> value.javaInstant.toKotlinInstant().toMeta()
    is UUID -> Meta(value.toString().asValue())
    is QualifiedName -> Meta {
        "namespaceIndex" put value.namespaceIndex
        "name" put value.name?.asValue()
    }
    is LocalizedText -> Meta {
        "locale" put value.locale?.asValue()
        "text" put value.text?.asValue()
    }
    is DataValue -> Meta {
        val variant= fromOpc(value.value)
        update(variant)// need SerializationContext to do that properly
        //TODO remove after DF 0.7.2
        this.value =  variant.value
        "@opc" put {
            value.statusCode.value.let { "status" put Meta(it.asValue()) }
            value.sourceTime?.javaInstant?.let { "sourceTime" put it.toKotlinInstant().toMeta() }
            value.sourcePicoseconds?.let { "sourcePicoseconds" put Meta(it.asValue()) }
            value.serverTime?.javaInstant?.let { "serverTime" put it.toKotlinInstant().toMeta() }
            value.serverPicoseconds?.let { "serverPicoseconds" put Meta(it.asValue()) }
        }
    }
    is ByteString -> Meta(value.bytesOrEmpty().asValue())
    is XmlElement -> Meta(value.fragment?.asValue() ?: Null)
    is NodeId -> Meta(value.toParseableString().asValue())
    is ExpandedNodeId -> Meta(value.toParseableString().asValue())
    is StatusCode -> Meta(value.value.asValue())
    //is ExtensionObject -> value.decode(client.getDynamicSerializationContext())
    else -> error("Could not create Meta for value: $value")
}

public fun Variant.toMeta(serializationContext: EncodingContext): Meta = (value as? ExtensionObject)?.let {
    it.decode(serializationContext) as Meta
} ?: Meta.fromOpc(value)