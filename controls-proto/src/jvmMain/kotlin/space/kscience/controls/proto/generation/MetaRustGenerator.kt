package space.kscience.controls.proto.generation

import space.kscience.controls.api.Device
import space.kscience.controls.spec.*
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import java.io.File


public object MetaRustGenerator {

    private data class MetaNodeInfo(
        val name: String,
        val rustType: String?,
        val children: List<MetaNodeInfo>,
    )

    private data class MetaModelInfo(
        val propertyName: String,
        val moduleName: String,
        val rootStructName: String,
        val readFunctionName: String,
        val writeFunctionName: String,
        val rootNode: MetaNodeInfo,
    )

    private data class PropertyInfo(
        val name: String,
        val rustType: String,
        val readable: Boolean,
        val mutable: Boolean,
        val metaModel: MetaModelInfo?,
    )

    private data class GenerationContext(
        val properties: List<PropertyInfo>,
        val readableProperties: List<PropertyInfo>,
        val mutableProperties: List<PropertyInfo>,
        val metaModels: List<MetaModelInfo>,
    )

    private fun DeviceSpec<*>.propertyInfos(): List<PropertyInfo> = properties.values
        .filter { !it.name.startsWith("@") }
        .map { prop ->
            val rustTypeAttr = prop.descriptor.metaDescriptor.attributes["rust_type"].string
            val resolvedRustType = rustTypeAttr ?: when (prop.converter) {
                MetaConverter.double -> "f64"
                MetaConverter.string -> "string"
                else -> "bytes"
            }
            val metaModel = if (resolvedRustType == "meta") {
                buildMetaModel(prop.name, prop.descriptor.metaDescriptor)
            } else {
                null
            }
            PropertyInfo(
                name = prop.name,
                rustType = resolvedRustType,
                readable = prop.descriptor.readable,
                mutable = prop.descriptor.mutable,
                metaModel = metaModel,
            )
        }

    private fun DeviceSpec<*>.generationContext(): GenerationContext {
        val properties = propertyInfos()
        return GenerationContext(
            properties = properties,
            readableProperties = properties.filter { it.readable },
            mutableProperties = properties.filter { it.mutable },
            metaModels = properties.mapNotNull { it.metaModel },
        )
    }

    private fun buildMetaModel(propertyName: String, descriptor: MetaDescriptor): MetaModelInfo? {
        val rootChildren = descriptor.nodes
            .entries
            .map { (childName, childDescriptor) ->
                childDescriptor.toMetaNodeInfo(childName)
            }

        if (rootChildren.isEmpty()) return null

        val rootNode = MetaNodeInfo(
            name = propertyName,
            rustType = null,
            children = rootChildren,
        )

        val propertyFunctionName = propertyName.toRustFunctionName()
        val moduleName = propertyName.toRustFieldName()
        return MetaModelInfo(
            propertyName = propertyName,
            moduleName = moduleName,
            rootStructName = "$moduleName::Model",
            readFunctionName = "read_${propertyFunctionName}_meta",
            writeFunctionName = "write_${propertyFunctionName}_meta",
            rootNode = rootNode,
        )
    }

    private fun MetaDescriptor.toMetaNodeInfo(nodeName: String): MetaNodeInfo {
        val childNodes = nodes
            .entries
            .map { (childName, childDescriptor) ->
                childDescriptor.toMetaNodeInfo(childName)
            }
        val resolvedRustType = if (childNodes.isEmpty()) {
            resolveDescriptorRustType(this)
        } else {
            null
        }
        return MetaNodeInfo(
            name = nodeName,
            rustType = resolvedRustType,
            children = childNodes,
        )
    }

    private fun resolveDescriptorRustType(nodeDescriptor: MetaDescriptor): String {
        val explicitRustType = nodeDescriptor.attributes["rust_type"].string?.lowercase()
        if (explicitRustType != null) {
            return when (explicitRustType) {
                "int", "i32" -> "i32"
                "long", "i64" -> "i64"
                "float", "f32" -> "f32"
                "double", "f64" -> "f64"
                "bool", "boolean" -> "bool"
                "string", "utf8" -> "String"
                else -> "String"
            }
        }

        val valueTypes = nodeDescriptor.valueTypes ?: emptyList()
        return when {
            valueTypes.contains(ValueType.BOOLEAN) -> "bool"
            valueTypes.contains(ValueType.STRING) -> "String"
            valueTypes.contains(ValueType.NUMBER) -> "f64"
            else -> "String"
        }
    }

    private fun String.identifierWords(): List<String> {
        val normalized = this
            .replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return listOf("generated")
        return normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun String.toPascalCase(): String = identifierWords().joinToString(separator = "") { word ->
        word.lowercase().replaceFirstChar { char -> char.uppercase() }
    }

    private fun String.toSnakeCase(): String = identifierWords().joinToString(separator = "_") { word ->
        word.lowercase()
    }

    private fun String.sanitizeRustIdentifier(fallback: String): String {
        val cleaned = replace(Regex("[^A-Za-z0-9_]"), "_")
        val nonEmpty = if (cleaned.isBlank()) fallback else cleaned
        return if (nonEmpty.firstOrNull()?.isDigit() == true) {
            "_$nonEmpty"
        } else {
            nonEmpty
        }
    }

    private fun String.toRustTypeName(): String = toPascalCase().sanitizeRustIdentifier("Generated")

    private fun String.toRustFunctionName(): String = toSnakeCase().sanitizeRustIdentifier("generated_fn")

    private fun String.toRustFieldName(): String = toSnakeCase().sanitizeRustIdentifier("field")

    private fun childStructName(parentStructName: String, childName: String): String =
        "$parentStructName${childName.toRustTypeName()}"

    private fun rustTypeToMetaGetterFunction(rustType: String): String = when (rustType) {
        "i32" -> "get_meta_i32"
        "i64" -> "get_meta_i64"
        "f32" -> "get_meta_f32"
        "f64" -> "get_meta_f64"
        "bool" -> "get_meta_bool"
        "String" -> "get_meta_string"
        else -> "get_meta_string"
    }

    private fun rustTypeToValueVariant(rustType: String, valueExpr: String): String = when (rustType) {
        "i32" -> "Value::Int32Value($valueExpr)"
        "i64" -> "Value::Int64Value($valueExpr)"
        "f32" -> "Value::FloatValue($valueExpr)"
        "f64" -> "Value::DoubleValue($valueExpr)"
        "bool" -> "Value::BooleanValue($valueExpr)"
        "String" -> "Value::StringValue($valueExpr.clone())"
        else -> "Value::StringValue(String::from(\"unsupported\"))"
    }

    private fun rustModule(name: String, init: RustFile.() -> Unit): String {
        val body = rustFile {
            use("super::*")
            init()
        }.toString().trimEnd().prependIndent("    ")
        return buildString {
            append("pub mod $name {\n")
            append(body)
            append('\n')
            append("}\n")
        }
    }

    private fun RustFile.emitCodecHelpers() {
        +"""
#[derive(Debug)]
struct SliceWriter<'a> {
    buffer: &'a mut [u8],
    used: usize,
}

impl<'a> SliceWriter<'a> {
    fn new(buffer: &'a mut [u8]) -> Self {
        Self { buffer, used: 0 }
    }

    fn written_len(&self) -> usize {
        self.used
    }
}

impl PbWrite for SliceWriter<'_> {
    type Error = ();

    fn pb_write(&mut self, data: &[u8]) -> Result<(), Self::Error> {
        let end = self.used.checked_add(data.len()).ok_or(())?;
        let target = self.buffer.get_mut(self.used..end).ok_or(())?;
        target.copy_from_slice(data);
        self.used = end;
        Ok(())
    }
}
""".trimIndent()

        fn("insert_meta_value", args = "meta: &mut ProtoMeta, key: &str, value: Value", returnType = "()", visibility = "pub(super)") {
            +"let mut item: ProtoMeta = Default::default();"
            +"item.set_protoValue(ProtoValue {"
            +"    r#value: Some(value),"
            +"});"
            +"meta.items.insert(key.to_string(), item);"
        }

        fn("insert_meta_node", args = "meta: &mut ProtoMeta, key: &str, value: ProtoMeta", returnType = "()", visibility = "pub(super)") {
            +"meta.items.insert(key.to_string(), value);"
        }

        fn("write_response_message", args = "response_meta: ProtoMeta, output: &mut [u8]", returnType = "Result<usize, ProtocolError>", visibility = "pub(super)") {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            +"envelope.set_meta(response_meta);"
            +"envelope.r#dataBytes = Vec::new();"
            +""
            +"let required_len = envelope.compute_size();"
            +"if required_len > output.len() {"
            +"    defmt::error!(\"\\nResponse requires {} bytes, but output buffer has only {}\", required_len, output.len());"
            +"    return Err(ProtocolError::OutputBufferTooSmall);"
            +"}"
            +"let mut encoder = PbEncoder::new(SliceWriter::new(output));"
            +"if envelope.encode(&mut encoder).is_err() {"
            +"    defmt::error!(\"\\nFailed to serialize response\");"
            +"    return Err(ProtocolError::EncodeResponse);"
            +"}"
            +"Ok(encoder.into_writer().written_len())"
        }

        fn("get_meta_i32", args = "meta: &ProtoMeta, key: &str", returnType = "Result<i32, &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::Int32Value(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected Int32Value\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_i64", args = "meta: &ProtoMeta, key: &str", returnType = "Result<i64, &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::Int64Value(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected Int64Value\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_f32", args = "meta: &ProtoMeta, key: &str", returnType = "Result<f32, &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::FloatValue(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected FloatValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_f64", args = "meta: &ProtoMeta, key: &str", returnType = "Result<f64, &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::DoubleValue(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected DoubleValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_bool", args = "meta: &ProtoMeta, key: &str", returnType = "Result<bool, &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::BooleanValue(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected BooleanValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_string", args = "meta: &ProtoMeta, key: &str", returnType = "Result<String, &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::StringValue(value)) => Ok(value.clone()),"
            +"        Some(_) => Err(\"Type mismatch: expected StringValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_present", args = "meta: &ProtoMeta, key: &str", returnType = "Result<(), &'static str>", visibility = "pub(super)") {
            +"match meta.items.get(key) {"
            +"    Some(item) => {"
            +"        if item.protoValue.value.is_some() || !item.items.is_empty() {"
            +"            Ok(())"
            +"        } else {"
            +"            Err(\"Empty meta field\")"
            +"        }"
            +"    }"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }
    }

    private fun RustFile.emitRuntimePrelude() {
        use("alloc::vec::Vec")
        use("alloc::string::{String, ToString}")
        use("micropb::{MessageDecode, MessageEncode, PbDecoder, PbEncoder, PbWrite}")

        +"mod proto {"
        +"    #![allow(clippy::all)]"
        +"    #![allow(nonstandard_style, unused, irrefutable_let_patterns)]"
        +"    include!(concat!(env!(\"OUT_DIR\"), \"/meta.rs\"));"
        +"}"

        use("proto::space_::kscience_::dataforge_::io_::proto_::{ProtoMeta, ProtoEnvelope}")
        use("proto::space_::kscience_::dataforge_::io_::proto_::ProtoMeta_::ProtoValue")
        use("proto::space_::kscience_::dataforge_::io_::proto_::ProtoMeta_::ProtoValue_::Value")
    }

    private fun RustFile.emitSupportDefinitions(metaModels: List<MetaModelInfo>) {
        emitCodecHelpers()
        metaModels.forEach { model ->
            emitMetaModel(model)
        }
    }

    private fun RustFile.emitProtocolErrorType() {
        +"""
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ProtocolError {
    DecodeRequest,
    MissingMethod,
    MissingMethodValue,
    InvalidMethodType,
    UnknownMethod,
    InvalidRequest(&'static str),
    InvalidPropertyValue(&'static str),
    ResponseMismatch,
    OutputBufferTooSmall,
    EncodeResponse,
}
""".trimIndent()
    }

    private fun RustFile.emitHostRequestEnum(
        readableProperties: List<PropertyInfo>,
        mutableProperties: List<PropertyInfo>,
    ) {
        +"#[derive(Debug, Clone)]"
        +"pub enum HostRequest {"

        readableProperties.forEach { property ->
            +"    ${property.getRequestVariantName()},"
        }

        mutableProperties.forEach { property ->
            +"    ${property.setRequestVariantName()}(${property.propertyValueType()}),"
        }

        +"}"
    }

    private fun RustFile.emitHostResponseEnum(properties: List<PropertyInfo>) {
        +"#[derive(Debug, Clone)]"
        +"pub enum HostResponse {"
        properties.forEach { property ->
            +"    ${property.responseVariantName()}(${property.propertyValueType()}),"
        }
        +"}"
    }

    private fun RustFile.emitMetaModel(model: MetaModelInfo) {
        val localRootStructName = "Model"
        custom(
            rustModule(model.moduleName) {
                emitMetaStructs(model.rootNode, localRootStructName)
                emitNodeCodecFunctions(model.rootNode, localRootStructName, model.propertyName)
            }
        )

        val rootCodecFunctionBase = localRootStructName.toRustFunctionName()
        fn(
            model.readFunctionName,
            args = "meta: &ProtoMeta, key: &str",
            returnType = "Result<${model.rootStructName}, &'static str>",
            visibility = "pub(super)",
        ) {
            +"let root_meta = meta.items.get(key).ok_or(\"Missing field\")?;"
            +"${model.moduleName}::decode_${rootCodecFunctionBase}_meta(root_meta)"
        }

        fn(
            model.writeFunctionName,
            args = "value: &${model.rootStructName}",
            returnType = "ProtoMeta",
            visibility = "pub(super)",
        ) {
            +"${model.moduleName}::encode_${rootCodecFunctionBase}_meta(value)"
        }
    }

    private fun RustFile.emitMetaStructs(node: MetaNodeInfo, structName: String) {
        if (node.children.isEmpty()) return

        node.children.filter { it.children.isNotEmpty() }.forEach { childNode ->
            emitMetaStructs(childNode, childStructName(structName, childNode.name))
        }

        struct(structName) {
            node.children.forEach { childNode ->
                val fieldName = childNode.name.toRustFieldName()
                val fieldType = if (childNode.children.isNotEmpty()) {
                    childStructName(structName, childNode.name)
                } else {
                    childNode.rustType ?: "String"
                }
                field(fieldName, fieldType)
            }
        }
    }

    private fun RustFile.emitNodeCodecFunctions(node: MetaNodeInfo, structName: String, pathPrefix: String) {
        if (node.children.isEmpty()) return

        val codecBase = structName.toRustFunctionName()

        fn(
            "decode_${codecBase}_meta",
            args = "meta: &ProtoMeta",
            returnType = "Result<$structName, &'static str>",
            visibility = "pub(super)",
        ) {
            +"Ok($structName {"
            node.children.forEach { childNode ->
                val fieldName = childNode.name.toRustFieldName()
                val childPath = "$pathPrefix.${childNode.name}"
                if (childNode.children.isNotEmpty()) {
                    val nestedStructName = childStructName(structName, childNode.name)
                    val nestedCodecBase = nestedStructName.toRustFunctionName()
                    +"    $fieldName: {"
                    +"        let child_meta = meta.items.get(\"${childNode.name}\").ok_or(\"Missing field: $childPath\")?;"
                    +"        decode_${nestedCodecBase}_meta(child_meta)?"
                    +"    },"
                } else {
                    val leafRustType = childNode.rustType ?: "String"
                    val getterFunction = rustTypeToMetaGetterFunction(leafRustType)
                    +"    $fieldName: ${getterFunction}(meta, \"${childNode.name}\").map_err(|_| \"Invalid field: $childPath\")?,"
                }
            }
            +"})"
        }

        fn(
            "encode_${codecBase}_meta",
            args = "value: &$structName",
            returnType = "ProtoMeta",
            visibility = "pub(super)",
        ) {
            +"let mut out: ProtoMeta = Default::default();"
            +"encode_${codecBase}_meta_into(value, &mut out);"
            +"out"
        }

        fn(
            "encode_${codecBase}_meta_into",
            args = "value: &$structName, out: &mut ProtoMeta",
            returnType = "()",
            visibility = "pub(super)",
        ) {
            node.children.forEach { childNode ->
                val fieldName = childNode.name.toRustFieldName()
                if (childNode.children.isNotEmpty()) {
                    val nestedStructName = childStructName(structName, childNode.name)
                    val nestedCodecBase = nestedStructName.toRustFunctionName()
                    +"{"
                    +"    let mut child_meta: ProtoMeta = Default::default();"
                    +"    encode_${nestedCodecBase}_meta_into(&value.$fieldName, &mut child_meta);"
                    +"    insert_meta_node(out, \"${childNode.name}\", child_meta);"
                    +"}"
                } else {
                    val leafRustType = childNode.rustType ?: "String"
                    val valueVariant = rustTypeToValueVariant(leafRustType, "value.$fieldName")
                    +"insert_meta_value(out, \"${childNode.name}\", $valueVariant);"
                }
            }
        }

        node.children.filter { it.children.isNotEmpty() }.forEach { childNode ->
            val nestedStructName = childStructName(structName, childNode.name)
            emitNodeCodecFunctions(childNode, nestedStructName, "$pathPrefix.${childNode.name}")
        }
    }

    private fun PropertyInfo.valueVariantExpr(valueExpr: String): String = when (rustType) {
        "int", "i32" -> "Value::Int32Value($valueExpr)"
        "long", "i64" -> "Value::Int64Value($valueExpr)"
        "float", "f32" -> "Value::FloatValue($valueExpr)"
        "double", "f64" -> "Value::DoubleValue($valueExpr)"
        "string", "utf8" -> "Value::StringValue($valueExpr)"
        "bool", "boolean" -> "Value::BooleanValue($valueExpr)"
        "meta" -> "Value::StringValue(String::from(\"meta\"))"
        else -> "Value::StringValue(String::from(\"unsupported\"))"
    }

    private fun PropertyInfo.propertyValueType(): String = when (rustType) {
        "int", "i32" -> "i32"
        "long", "i64" -> "i64"
        "float", "f32" -> "f32"
        "double", "f64" -> "f64"
        "string", "utf8" -> "String"
        "bool", "boolean" -> "bool"
        "meta" -> requireNotNull(metaModel) {
            "Meta property '$name' requires a structured descriptor to generate host request types"
        }.rootStructName
        else -> "String"
    }

    private fun PropertyInfo.getRequestVariantName(): String = "Get${name.toRustTypeName()}"

    private fun PropertyInfo.setRequestVariantName(): String = "Set${name.toRustTypeName()}"

    private fun PropertyInfo.responseVariantName(): String = name.toRustTypeName()

    private fun PropertyInfo.requestDecodeExpression(): String = when (rustType) {
        "meta" -> {
            val model = requireNotNull(metaModel) {
                "Meta property '$name' requires a structured descriptor to generate host request types"
            }
            "${model.readFunctionName}(meta, \"$name\")"
        }
        else -> "${postGetterFunction()}(meta, \"$name\")"
    }

    private fun PropertyInfo.responseInsertStatement(valueExpr: String): String = when (rustType) {
        "meta" -> {
            val model = requireNotNull(metaModel) {
                "Meta property '$name' requires a structured descriptor to generate host responses"
            }
            "insert_meta_node(&mut response_meta, \"$name\", ${model.writeFunctionName}(&$valueExpr));"
        }
        else -> "insert_meta_value(&mut response_meta, \"$name\", ${valueVariantExpr(valueExpr)});"
    }

    private fun PropertyInfo.postGetterFunction(): String = when (rustType) {
        "int", "i32" -> "get_meta_i32"
        "long", "i64" -> "get_meta_i64"
        "float", "f32" -> "get_meta_f32"
        "double", "f64" -> "get_meta_f64"
        "string", "utf8" -> "get_meta_string"
        "bool", "boolean" -> "get_meta_bool"
        "meta" -> metaModel?.readFunctionName ?: "get_meta_present"
        else -> "get_meta_present"
    }

    private fun RustFunction.emitDecodeGetRequests(readableProperties: List<PropertyInfo>) {
        if (readableProperties.isEmpty()) {
            +"defmt::warn!(\"\\nGET request has no known fields to respond with\");"
            +"return Err(ProtocolError::InvalidRequest(\"GET request has no known fields\"));"
            return
        }

        readableProperties.forEachIndexed { index, property ->
            val branchStart = if (index == 0) "if" else "else if"
            +"$branchStart meta.items.contains_key(\"${property.name}\") {"
            +"    defmt::info!(\"\\nGET request for property '${property.name}'\");"
            +"    return Ok(HostRequest::${property.getRequestVariantName()});"
            +"}"
        }

        +"else {"
        +"    defmt::warn!(\"\\nGET request has no known fields to respond with\");"
        +"    return Err(ProtocolError::InvalidRequest(\"GET request has no known fields\"));"
        +"}"
    }

    private fun RustFunction.emitDecodePostRequests(mutableProperties: List<PropertyInfo>) {
        if (mutableProperties.isEmpty()) {
            +"defmt::warn!(\"\\nPOST request has no known fields to apply\");"
            +"return Err(ProtocolError::InvalidRequest(\"POST request has no known fields\"));"
            return
        }

        mutableProperties.forEachIndexed { index, property ->
            val branchStart = if (index == 0) "if" else "else if"
            +"$branchStart meta.items.contains_key(\"${property.name}\") {"
            +"    let value = match ${property.requestDecodeExpression()} {"
            +"        Ok(value) => value,"
            +"        Err(error) => {"
            +"            defmt::warn!(\"\\nInvalid property value for '${property.name}': {}\", error);"
            +"            return Err(ProtocolError::InvalidPropertyValue(error));"
            +"        }"
            +"    };"
            +"    return Ok(HostRequest::${property.setRequestVariantName()}(value));"
            +"}"
        }

        +"else {"
        +"    defmt::warn!(\"\\nPOST request has no known fields to apply\");"
        +"    return Err(ProtocolError::InvalidRequest(\"POST request has no known fields\"));"
        +"}"
    }

    private fun RustFunction.emitDecodeMethodDispatch(
        readableProperties: List<PropertyInfo>,
        mutableProperties: List<PropertyInfo>,
    ) {
        matchBlock("method") {
            arm("\"GET\"") {
                emitDecodeGetRequests(readableProperties)
            }
            arm("\"POST\"") {
                emitDecodePostRequests(mutableProperties)
            }
            arm("_") {
                +"defmt::warn!(\"\\nUnknown method: {} (expected GET or POST)\", method);"
                +"return Err(ProtocolError::UnknownMethod);"
            }
        }
    }

    private fun RustFile.emitDecodeHostRequest(
        readableProperties: List<PropertyInfo>,
        mutableProperties: List<PropertyInfo>,
    ) {
        fn("decode_host_request", args = "buffer: &[u8]", returnType = "Result<HostRequest, ProtocolError>", visibility = "") {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            +"let mut decoder = PbDecoder::new(buffer);"
            ifBlock("envelope.decode(&mut decoder, buffer.len()).is_err()") {
                +"defmt::error!(\"\\nFailed to decode ProtoEnvelope\");"
                +"return Err(ProtocolError::DecodeRequest);"
            }
            +"defmt::info!(\"\\nDeserialized {} bytes\", buffer.len());"

            +"let meta = &envelope.meta;"
            +"let method = match meta.items.get(\"method\") {"
            +"    Some(m_item) => match &m_item.protoValue.value {"
            +"        Some(Value::StringValue(s)) => s.as_str(),"
            +"        Some(_) => {"
            +"            defmt::error!(\"\\nInvalid 'method' type: expected string ('GET' or 'POST')\");"
            +"            return Err(ProtocolError::InvalidMethodType);"
            +"        }"
            +"        None => {"
            +"            defmt::error!(\"\\nMissing 'method' value\");"
            +"            return Err(ProtocolError::MissingMethodValue);"
            +"        }"
            +"    },"
            +"    None => {"
            +"        defmt::error!(\"\\nMissing 'method' field\");"
            +"        return Err(ProtocolError::MissingMethod);"
            +"    }"
            +"};"

            emitDecodeMethodDispatch(readableProperties, mutableProperties)
        }
    }

    private fun RustFile.emitEncodeHostResponse(
        readableProperties: List<PropertyInfo>,
        mutableProperties: List<PropertyInfo>,
    ) {
        fn(
            "encode_host_response",
            args = "request: HostRequest, response: HostResponse, output: &mut [u8]",
            returnType = "Result<usize, ProtocolError>",
            visibility = "",
        ) {
            +"let mut response_meta: ProtoMeta = Default::default();"
            +"match (request, response) {"

            readableProperties.forEach { property ->
                +"    (HostRequest::${property.getRequestVariantName()}, HostResponse::${property.responseVariantName()}(value)) => {"
                +"        insert_meta_value(&mut response_meta, \"method\", Value::StringValue(String::from(\"GET\")));"
                +"        ${property.responseInsertStatement("value")}"
                +"    },"
            }

            mutableProperties.forEach { property ->
                +"    (HostRequest::${property.setRequestVariantName()}(_), HostResponse::${property.responseVariantName()}(value)) => {"
                +"        insert_meta_value(&mut response_meta, \"method\", Value::StringValue(String::from(\"POST\")));"
                +"        ${property.responseInsertStatement("value")}"
                +"    },"
            }

            +"    _ => {"
            +"        defmt::warn!(\"\\nHost response does not match request\");"
            +"        return Err(ProtocolError::ResponseMismatch);"
            +"    },"
            +"}"
            +"write_response_message(response_meta, output)"
        }
    }

    private fun RustFile.emitTryHandleHostMessage() {
        fn(
            "try_handle_host_message",
            args = "buffer: &[u8], output: &mut [u8], mut on_request: impl FnMut(HostRequest) -> Option<HostResponse>",
            returnType = "Result<usize, ProtocolError>",
            visibility = "",
        ) {
            +"let request = decode_host_request(buffer)?;"
            +"let response = match on_request(request.clone()) {"
            +"    Some(response) => response,"
            +"    None => return Ok(0),"
            +"};"
            +"encode_host_response(request, response, output)"
        }
    }

    private fun RustFile.emitHandleHostMessage() {
        fn(
            "handle_host_message",
            args = "buffer: &[u8], output: &mut [u8], on_request: impl FnMut(HostRequest) -> Option<HostResponse>",
            returnType = "usize",
            visibility = "",
        ) {
            +"match try_handle_host_message(buffer, output, on_request) {"
            +"    Ok(written) => written,"
            +"    Err(_error) => 0,"
            +"}"
        }
    }

    private fun RustFile.emitCompatibilityHandleMessage() {
        fn(
            "handle_message",
            args = "buffer: &[u8], output: &mut [u8], on_request: impl FnMut(HostRequest) -> Option<HostResponse>",
            returnType = "usize",
        ) {
            +"handle_host_message(buffer, output, on_request)"
        }
    }

    private fun RustFile.emitApiFacade(context: GenerationContext, codecModuleName: String) {
        +"#[path = \"$codecModuleName.rs\"]"
        +"mod $codecModuleName;"
        +""
        +"pub use $codecModuleName::{"
        +"    handle_message,"
        +"    HostRequest,"
        +"    HostResponse,"
        +"};"

        if (context.metaModels.isNotEmpty()) {
            +""
            +"pub use $codecModuleName::{"
            context.metaModels.forEach { model ->
                +"    ${model.moduleName},"
            }
            +"};"
        }
    }

    public fun generateToFile(deviceSpec: DeviceSpec<*>, path: String = "build/generated/rust/device.rs") {
        val apiFile = File(path)
        apiFile.parentFile?.mkdirs()

        val baseModuleName = apiFile.nameWithoutExtension.toRustFieldName()
        val codecModuleName = "${baseModuleName}_codec"
        val supportModuleName = "${baseModuleName}_support"
        val outputDirectory = apiFile.parentFile ?: File(".")
        val codecFile = File(outputDirectory, "$codecModuleName.rs")
        val supportFile = File(outputDirectory, "$supportModuleName.rs")

        supportFile.writeText(generateDeviceSupportModule(deviceSpec))
        codecFile.writeText(generateDeviceCodecModule(deviceSpec, supportModuleName))
        apiFile.writeText(generateDeviceApiModule(deviceSpec, codecModuleName))
    }

    public fun generateDeviceApiModule(
        deviceSpec: DeviceSpec<*>,
        codecModuleName: String = "device_codec",
    ): String {
        val context = deviceSpec.generationContext()
        return rustFile {
            emitApiFacade(context, codecModuleName)
        }.toString()
    }

    public fun generateDeviceSupportModule(deviceSpec: DeviceSpec<*>): String {
        val context = deviceSpec.generationContext()
        return rustFile {
            use("super::*")
            emitSupportDefinitions(context.metaModels)
        }.toString()
    }

    public fun generateDeviceCodecModule(
        deviceSpec: DeviceSpec<*>,
        supportModuleName: String = "device_support",
    ): String {
        val context = deviceSpec.generationContext()
        return rustFile {
            emitRuntimePrelude()
            emitProtocolErrorType()
            +"#[path = \"$supportModuleName.rs\"]"
            +"mod $supportModuleName;"
            +"pub use $supportModuleName::*;"
            emitHostRequestEnum(context.readableProperties, context.mutableProperties)
            emitHostResponseEnum(context.properties)
            emitDecodeHostRequest(context.readableProperties, context.mutableProperties)
            emitEncodeHostResponse(context.readableProperties, context.mutableProperties)
            emitTryHandleHostMessage()
            emitHandleHostMessage()
            emitCompatibilityHandleMessage()
        }.toString()
    }

    public fun generateSplitDeviceHandler(
        deviceSpec: DeviceSpec<*>,
        supportModuleName: String = "device_support",
    ): String = generateDeviceCodecModule(deviceSpec, supportModuleName)

    public fun generateDeviceHandler(deviceSpec: DeviceSpec<*>): String {
        val context = deviceSpec.generationContext()
        return rustFile {
            emitRuntimePrelude()
            emitProtocolErrorType()
            emitHostRequestEnum(context.readableProperties, context.mutableProperties)
            emitHostResponseEnum(context.properties)
            emitSupportDefinitions(context.metaModels)
            emitDecodeHostRequest(context.readableProperties, context.mutableProperties)
            emitEncodeHostResponse(context.readableProperties, context.mutableProperties)
            emitTryHandleHostMessage()
            emitHandleHostMessage()
            emitCompatibilityHandleMessage()
        }.toString()
    }
    
    // Kept for compatibility
    public fun generateRust(): String = generateDeviceHandler(object : DeviceSpec<Device>() {})

    @JvmStatic
    public fun main(args: Array<String>) {
         // No-op
    }
}
