//package space.kscience.controls.opcua.client
//
//import org.eclipse.milo.opcua.sdk.core.dtd.AbstractBsdCodec
//import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext
//import org.eclipse.milo.opcua.stack.core.types.builtin.*
//import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.*
//import org.opcfoundation.opcua.binaryschema.StructuredType
//import space.kscience.controls.instant
//import space.kscience.controls.toMeta
//import space.kscience.dataforge.meta.*
//import space.kscience.dataforge.names.Name
//import space.kscience.dataforge.names.asName
//import java.util.*
//import kotlin.time.toJavaInstant
//import kotlin.time.toKotlinInstant
//
//
////public class MetaBsdParser : BsdParser() {
////    override fun getEnumCodec(enumeratedType: EnumeratedType): OpcUaBinaryDataTypeCodec<*> {
////        return MetaEnumCodec()
////    }
////
////    override fun getStructCodec(structuredType: StructuredType): OpcUaBinaryDataTypeCodec<*> {
////        return MetaStructureCodec(structuredType)
////    }
////}
////
////internal class MetaEnumCodec : OpcUaBinaryDataTypeCodec<Number> {
////    override fun getType(): Class<Number> {
////        return Number::class.java
////    }
////
////    @Throws(UaSerializationException::class)
////    override fun encode(
////        context: SerializationContext,
////        encoder: OpcUaBinaryStreamEncoder,
////        value: Number
////    ) {
////        encoder.writeInt32(value.toInt())
////    }
////
////    @Throws(UaSerializationException::class)
////    override fun decode(
////        context: SerializationContext,
////        decoder: OpcUaBinaryStreamDecoder
////    ): Number {
////        return decoder.readInt32()
////    }
////}
//
//
///**
// * based on https://github.com/eclipse/milo/blob/master/opc-ua-stack/bsd-parser-gson/src/main/java/org/eclipse/milo/opcua/binaryschema/gson/JsonStructureCodec.java
// */
//internal class BinaryMetaCodec(
//    structuredType: StructuredType?
//) : AbstractBsdCodec<Meta, Meta>(structuredType) {
//
//    override fun createStructure(name: String, members: LinkedHashMap<String, Meta>): Meta = Meta {
//        members.forEach { (property: String, value: Meta?) ->
//            set(Name.parse(property), value)
//        }
//    }
//
//    override fun opcUaToMemberTypeScalar(name: String, value: Any?, typeName: String): Meta = opcToMeta(value)
//
//    override fun opcUaToMemberTypeArray(name: String, values: Any?, typeName: String): Meta = if (values == null) {
//        Meta(Null)
//    } else {
//        // This is a bit array...
//        when (values) {
//            is DoubleArray -> Meta(values.asValue())
//            is FloatArray -> Meta(values.asValue())
//            is IntArray -> Meta(values.asValue())
//            is ByteArray -> Meta(values.asValue())
//            is ShortArray -> Meta(values.asValue())
//            is Array<*> -> Meta {
//                setIndexed(Name.parse(name), values.map { opcUaToMemberTypeScalar(name, it, typeName) })
//            }
//            is Number -> Meta(values.asValue())
//            else -> error("Could not create Meta for value: $values")
//        }
//    }
//
//    override fun memberTypeToOpcUaScalar(member: Meta?, typeName: String): Any? =
//        if (member == null || member.isEmpty()) {
//            null
//        } else when (typeName) {
//            "Boolean" -> member.boolean
//            "SByte" -> member.value?.numberOrNull?.toByte()
//            "Int16" -> member.value?.numberOrNull?.toShort()
//            "Int32" -> member.value?.numberOrNull?.toInt()
//            "Int64" -> member.value?.numberOrNull?.toLong()
//            "Byte" -> member.value?.numberOrNull?.toShort()?.let { Unsigned.ubyte(it) }
//            "UInt16" -> member.value?.numberOrNull?.toInt()?.let { Unsigned.ushort(it) }
//            "UInt32" -> member.value?.numberOrNull?.toLong()?.let { Unsigned.uint(it) }
//            "UInt64" -> member.value?.numberOrNull?.toLong()?.let { Unsigned.ulong(it) }
//            "Float" -> member.value?.numberOrNull?.toFloat()
//            "Double" -> member.value?.numberOrNull?.toDouble()
//            "String" -> member.string
//            "DateTime" -> member.instant?.toJavaInstant()?.let { DateTime(it) }
//            "Guid" -> member.string?.let { UUID.fromString(it) }
//            "ByteString" -> member.value?.list?.let { list ->
//                ByteString(list.map { it.number.toByte() }.toByteArray())
//            }
//            "XmlElement" -> member.string?.let { XmlElement(it) }
//            "NodeId" -> member.string?.let { NodeId.parse(it) }
//            "ExpandedNodeId" -> member.string?.let { ExpandedNodeId.parse(it) }
//            "StatusCode" -> member.long?.let { StatusCode(it) }
//            "QualifiedName" -> QualifiedName(
//                member["namespaceIndex"].int ?: 0,
//                member["name"].string
//            )
//            "LocalizedText" -> LocalizedText(
//                member["locale"].string,
//                member["text"].string
//            )
//            else -> member.toString()
//        }
//
//    override fun memberTypeToOpcUaArray(member: Meta, typeName: String): Any = if ("Bit" == typeName) {
//        member.value?.int ?: error("Meta node does not contain int value")
//    } else {
//        when (typeName) {
//            "SByte" -> member.value?.list?.map { it.number.toByte() }?.toByteArray() ?: emptyArray<Byte>()
//            "Int16" -> member.value?.list?.map { it.number.toShort() }?.toShortArray() ?: emptyArray<Short>()
//            "Int32" -> member.value?.list?.map { it.number.toInt() }?.toIntArray() ?: emptyArray<Int>()
//            "Int64" -> member.value?.list?.map { it.number.toLong() }?.toLongArray() ?: emptyArray<Long>()
//            "Byte" -> member.value?.list?.map {
//                Unsigned.ubyte(it.number.toShort())
//            }?.toTypedArray() ?: emptyArray<UByte>()
//            "UInt16" -> member.value?.list?.map {
//                Unsigned.ushort(it.number.toInt())
//            }?.toTypedArray() ?: emptyArray<UShort>()
//            "UInt32" -> member.value?.list?.map {
//                Unsigned.uint(it.number.toLong())
//            }?.toTypedArray() ?: emptyArray<UInteger>()
//            "UInt64" -> member.value?.list?.map {
//                Unsigned.ulong(it.number.toLong())
//            }?.toTypedArray() ?: emptyArray<kotlin.ULong>()
//            "Float" -> member.value?.list?.map { it.number.toFloat() }?.toFloatArray() ?: emptyArray<Float>()
//            "Double" -> member.value?.list?.map { it.number.toDouble() }?.toDoubleArray() ?: emptyArray<Double>()
//            else -> member.getIndexed(Meta.JSON_ARRAY_KEY.asName()).map {
//                memberTypeToOpcUaScalar(it.value, typeName)
//            }.toTypedArray()
//        }
//    }
//
//    override fun getMembers(value: Meta): Map<String, Meta> = value.items.mapKeys { it.toString() }
//}
//
//
////public fun Meta.toVariant(): Variant = if (items.isEmpty()) {
////    Variant(value?.value)
////} else {
////    TODO()
////}
