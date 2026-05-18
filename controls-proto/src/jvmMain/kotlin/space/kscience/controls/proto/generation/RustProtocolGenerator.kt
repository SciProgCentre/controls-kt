package space.kscience.controls.proto.generation

import space.kscience.controls.api.Device
import space.kscience.controls.spec.DeviceSpec
import java.io.File

public object RustProtocolGenerator : ProtocolGenerator {
    private const val DEFAULT_API_PATH = "build/generated/rust/device.rs"
    private const val DEFAULT_CODEC_MODULE = "device_codec"
    private const val DEFAULT_SUPPORT_MODULE = "device_support"
    private const val CODEC_MODULE_SUFFIX = "_codec"
    private const val SUPPORT_MODULE_SUFFIX = "_support"
    private const val CRATE_README_PATH = "README.md"
    private const val CRATE_MANIFEST_PATH = "Cargo.toml"
    private const val CRATE_BUILD_SCRIPT_PATH = "build.rs"
    private const val CRATE_SOURCE_ROOT = "src"

    private const val ROOT_MODEL_NAME = "Model"
    private const val METHOD_FIELD = "method"
    private const val GET_METHOD = "GET"
    private const val POST_METHOD = "POST"

    private const val HOST_REQUEST_TYPE = "HostRequest"
    private const val HOST_RESPONSE_TYPE = "HostResponse"
    private const val HANDLE_MESSAGE_FUNCTION = "handle_message"
    private const val HANDLE_HOST_MESSAGE_FUNCTION = "handle_host_message"
    private const val TRY_HANDLE_HOST_MESSAGE_FUNCTION = "try_handle_host_message"
    private const val DECODE_HOST_REQUEST_FUNCTION = "decode_host_request"
    private const val ENCODE_HOST_RESPONSE_FUNCTION = "encode_host_response"

    private const val PRIVATE_VISIBILITY = ""
    private const val SUPPORT_VISIBILITY = "pub(super)"
    private const val ERROR_CODE_MODULE = "error_code"
    private const val NO_RESPONSE_CODE = "NO_RESPONSE"

    private data class ProtocolErrorCode(
        val constantName: String,
        val code: Int,
        val matchPattern: String,
    )

    private val protocolErrorCodes = listOf(
        ProtocolErrorCode("DECODE_REQUEST", -1, "ProtocolError::DecodeRequest"),
        ProtocolErrorCode("MISSING_METHOD", -2, "ProtocolError::MissingMethod"),
        ProtocolErrorCode("MISSING_METHOD_VALUE", -3, "ProtocolError::MissingMethodValue"),
        ProtocolErrorCode("INVALID_METHOD_TYPE", -4, "ProtocolError::InvalidMethodType"),
        ProtocolErrorCode("UNKNOWN_METHOD", -5, "ProtocolError::UnknownMethod"),
        ProtocolErrorCode("INVALID_REQUEST", -6, "ProtocolError::InvalidRequest"),
        ProtocolErrorCode("INVALID_PROPERTY_VALUE", -7, "ProtocolError::InvalidPropertyValue"),
        ProtocolErrorCode("RESPONSE_MISMATCH", -8, "ProtocolError::ResponseMismatch"),
        ProtocolErrorCode("OUTPUT_BUFFER_TOO_SMALL", -9, "ProtocolError::OutputBufferTooSmall"),
        ProtocolErrorCode("ENCODE_RESPONSE", -10, "ProtocolError::EncodeResponse"),
    )

    private val publicHandleMessageItems: List<String>
        get() = listOf(HANDLE_MESSAGE_FUNCTION, ERROR_CODE_MODULE, HOST_REQUEST_TYPE, HOST_RESPONSE_TYPE)

    private enum class RustScalar(
        val protocolScalar: ProtocolScalar,
        val rustType: String,
        val getterFunction: String,
        val protoValueVariant: String,
        private val cloneWhenBorrowed: Boolean = false,
    ) {
        I32(ProtocolScalar.INT32, "i32", "get_meta_i32", "Int32Value"),
        I64(ProtocolScalar.INT64, "i64", "get_meta_i64", "Int64Value"),
        F32(ProtocolScalar.FLOAT32, "f32", "get_meta_f32", "FloatValue"),
        F64(ProtocolScalar.FLOAT64, "f64", "get_meta_f64", "DoubleValue"),
        BOOL(ProtocolScalar.BOOLEAN, "bool", "get_meta_bool", "BooleanValue"),
        STRING(ProtocolScalar.STRING, "String", "get_meta_string", "StringValue", cloneWhenBorrowed = true);

        fun valueExpression(value: String, borrowed: Boolean = false): String {
            val payload = if (borrowed && cloneWhenBorrowed) "$value.clone()" else value
            return "Value::$protoValueVariant($payload)"
        }

        fun readExpression(value: String): String = if (cloneWhenBorrowed) {
            "Ok($value.clone())"
        } else {
            "Ok(*$value)"
        }

        companion object {
            fun fromProtocolScalar(scalar: ProtocolScalar): RustScalar =
                values().first { it.protocolScalar == scalar }
        }
    }

    private enum class HostMethod(val wireName: String, val noKnownFieldsMessage: String) {
        GET(GET_METHOD, "GET request has no known fields to respond with"),
        POST(POST_METHOD, "POST request has no known fields to apply");

        val matchPattern: String get() = "\"$wireName\""
        val responseMethodValue: String get() = "Value::StringValue(String::from(\"$wireName\"))"
    }

    private data class MetaModelInfo(
        val propertyName: String,
        val moduleName: String,
        val rootStructName: String,
        val readFunctionName: String,
        val writeFunctionName: String,
        val rootNode: ProtocolMetaNode,
    )

    private data class PropertyInfo(
        val name: String,
        val scalar: RustScalar?,
        val readable: Boolean,
        val writable: Boolean,
        val metaModel: MetaModelInfo?,
    )

    private data class GenerationContext(
        val properties: List<PropertyInfo>,
        val readableProperties: List<PropertyInfo>,
        val writableProperties: List<PropertyInfo>,
        val metaModels: List<MetaModelInfo>,
    )

    override val language: ProtocolLanguage get() = ProtocolLanguage.RUST

    private val PropertyInfo.isStructuredMeta: Boolean get() = metaModel != null

    private fun ProtocolSchema.generationContext(): GenerationContext {
        val properties = properties.map { property -> property.toRustPropertyInfo() }
        return GenerationContext(
            properties = properties,
            readableProperties = properties.filter { it.readable },
            writableProperties = properties.filter { it.writable },
            metaModels = properties.mapNotNull { it.metaModel },
        )
    }

    private fun DeviceSpec<*>.rustGenerationContext(): GenerationContext = toProtocolSchema().generationContext()

    private fun ProtocolProperty.toRustPropertyInfo(): PropertyInfo = when (val propertyType = type) {
        is ProtocolPropertyType.Scalar -> PropertyInfo(
            name = name,
            scalar = RustScalar.fromProtocolScalar(propertyType.scalar),
            readable = readable,
            writable = writable,
            metaModel = null,
        )
        is ProtocolPropertyType.StructuredMeta -> PropertyInfo(
            name = name,
            scalar = null,
            readable = readable,
            writable = writable,
            metaModel = buildMetaModel(propertyType.model),
        )
    }

    private fun buildMetaModel(model: ProtocolStructuredModel): MetaModelInfo {
        val propertyName = model.propertyName
        val moduleName = propertyName.toRustFieldName()
        val propertyFunctionName = propertyName.toRustFunctionName()
        return MetaModelInfo(
            propertyName = propertyName,
            moduleName = moduleName,
            rootStructName = "$moduleName::$ROOT_MODEL_NAME",
            readFunctionName = "read_${propertyFunctionName}_meta",
            writeFunctionName = "write_${propertyFunctionName}_meta",
            rootNode = model.rootNode,
        )
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

    private fun String.toRustPackageName(): String = toRustFieldName()

    private fun childStructName(parentStructName: String, childName: String): String =
        "$parentStructName${childName.toRustTypeName()}"

    private fun ProtocolMetaNode.leafScalar(): RustScalar =
        RustScalar.fromProtocolScalar(requireNotNull(scalar) { "Leaf meta node '$name' has no scalar type" })

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

    private fun RustFile.emitMetaGetter(scalar: RustScalar) {
        fn(
            scalar.getterFunction,
            args = "meta: &ProtoMeta, key: &str",
            returnType = "Result<${scalar.rustType}, &'static str>",
            visibility = SUPPORT_VISIBILITY,
        ) {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::${scalar.protoValueVariant}(value)) => ${scalar.readExpression("value")},"
            +"        Some(_) => Err(\"Type mismatch: expected ${scalar.protoValueVariant}\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
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

        fn("insert_meta_value", args = "meta: &mut ProtoMeta, key: &str, value: Value", returnType = "()", visibility = SUPPORT_VISIBILITY) {
            +"let mut item: ProtoMeta = Default::default();"
            +"item.set_protoValue(ProtoValue {"
            +"    r#value: Some(value),"
            +"});"
            +"meta.items.insert(key.to_string(), item);"
        }

        fn("insert_meta_node", args = "meta: &mut ProtoMeta, key: &str, value: ProtoMeta", returnType = "()", visibility = SUPPORT_VISIBILITY) {
            +"meta.items.insert(key.to_string(), value);"
        }

        fn("write_response_message", args = "response_meta: ProtoMeta, output: &mut [u8]", returnType = "Result<usize, ProtocolError>", visibility = SUPPORT_VISIBILITY) {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            +"envelope.set_meta(response_meta);"
            +"envelope.r#dataBytes = Vec::new();"
            +""
            +"let required_len = envelope.compute_size();"
            +"if required_len > output.len() {"
            +"    return Err(ProtocolError::OutputBufferTooSmall);"
            +"}"
            +"let mut encoder = PbEncoder::new(SliceWriter::new(output));"
            +"if envelope.encode(&mut encoder).is_err() {"
            +"    return Err(ProtocolError::EncodeResponse);"
            +"}"
            +"Ok(encoder.into_writer().written_len())"
        }

        RustScalar.values().forEach { scalar ->
            emitMetaGetter(scalar)
        }
    }

    private fun RustFile.emitRuntimePrelude(runtime: RustProtocolRuntime) {
        use("alloc::vec::Vec")
        use("alloc::string::{String, ToString}")
        use("micropb::{MessageDecode, MessageEncode, PbDecoder, PbEncoder, PbWrite}")

        runtime.outDirModule?.let { outDirModule ->
            +"mod ${outDirModule.moduleName} {"
            +"    #![allow(clippy::all)]"
            +"    #![allow(nonstandard_style, unused, irrefutable_let_patterns)]"
            +"    include!(concat!(env!(\"OUT_DIR\"), \"/${outDirModule.generatedFileName}\"));"
            +"}"
        }

        use("${runtime.protoModulePath}::{ProtoMeta, ProtoEnvelope}")
        use("${runtime.protoModulePath}::ProtoMeta_::ProtoValue")
        use("${runtime.protoModulePath}::ProtoMeta_::ProtoValue_::Value")
    }

    private fun RustFile.emitSupportDefinitions(metaModels: List<MetaModelInfo>) {
        emitCodecHelpers()
        metaModels.forEach { model ->
            emitMetaModel(model)
        }
    }

    private fun RustFile.emitHandleMessageReturnCodes() {
        +"pub mod $ERROR_CODE_MODULE {"
        +"    pub const $NO_RESPONSE_CODE: isize = 0;"
        protocolErrorCodes.forEach { errorCode ->
            +"    pub const ${errorCode.constantName}: isize = ${errorCode.code};"
        }
        +"}"
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
    InvalidRequest,
    InvalidPropertyValue,
    ResponseMismatch,
    OutputBufferTooSmall,
    EncodeResponse,
}
""".trimIndent()
    }

    private fun RustFile.emitProtocolErrorCodeFunction() {
        fn("protocol_error_code", args = "error: ProtocolError", returnType = "isize", visibility = PRIVATE_VISIBILITY) {
            matchBlock("error") {
                protocolErrorCodes.forEach { errorCode ->
                    arm(errorCode.matchPattern) {
                        +"$ERROR_CODE_MODULE::${errorCode.constantName}"
                    }
                }
            }
        }
    }

    private fun RustFile.emitHostRequestEnum(
        readableProperties: List<PropertyInfo>,
        writableProperties: List<PropertyInfo>,
    ) {
        +"#[derive(Debug, Clone)]"
        +"pub enum $HOST_REQUEST_TYPE {"

        readableProperties.forEach { property ->
            +"    ${property.getRequestVariantName()},"
        }

        writableProperties.forEach { property ->
            +"    ${property.setRequestVariantName()}(${property.propertyValueType()}),"
        }

        +"}"
    }

    private fun RustFile.emitHostResponseEnum(properties: List<PropertyInfo>) {
        +"#[derive(Debug, Clone)]"
        +"pub enum $HOST_RESPONSE_TYPE {"
        properties.forEach { property ->
            +"    ${property.responseVariantName()}(${property.propertyValueType()}),"
        }
        +"}"
    }

    private fun RustFile.emitMetaModel(model: MetaModelInfo) {
        val localRootStructName = ROOT_MODEL_NAME
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
            visibility = SUPPORT_VISIBILITY,
        ) {
            +"let root_meta = meta.items.get(key).ok_or(\"Missing field\")?;"
            +"${model.moduleName}::decode_${rootCodecFunctionBase}_meta(root_meta)"
        }

        fn(
            model.writeFunctionName,
            args = "value: &${model.rootStructName}",
            returnType = "ProtoMeta",
            visibility = SUPPORT_VISIBILITY,
        ) {
            +"${model.moduleName}::encode_${rootCodecFunctionBase}_meta(value)"
        }
    }

    private fun RustFile.emitMetaStructs(node: ProtocolMetaNode, structName: String) {
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
                    childNode.leafScalar().rustType
                }
                field(fieldName, fieldType)
            }
        }
    }

    private fun RustFile.emitNodeCodecFunctions(node: ProtocolMetaNode, structName: String, pathPrefix: String) {
        if (node.children.isEmpty()) return

        val codecBase = structName.toRustFunctionName()

        fn(
            "decode_${codecBase}_meta",
            args = "meta: &ProtoMeta",
            returnType = "Result<$structName, &'static str>",
            visibility = SUPPORT_VISIBILITY,
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
                    val getterFunction = childNode.leafScalar().getterFunction
                    +"    $fieldName: ${getterFunction}(meta, \"${childNode.name}\").map_err(|_| \"Invalid field: $childPath\")?,"
                }
            }
            +"})"
        }

        fn(
            "encode_${codecBase}_meta",
            args = "value: &$structName",
            returnType = "ProtoMeta",
            visibility = SUPPORT_VISIBILITY,
        ) {
            +"let mut out: ProtoMeta = Default::default();"
            +"encode_${codecBase}_meta_into(value, &mut out);"
            +"out"
        }

        fn(
            "encode_${codecBase}_meta_into",
            args = "value: &$structName, out: &mut ProtoMeta",
            returnType = "()",
            visibility = SUPPORT_VISIBILITY,
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
                    val valueVariant = childNode.leafScalar().valueExpression("value.$fieldName", borrowed = true)
                    +"insert_meta_value(out, \"${childNode.name}\", $valueVariant);"
                }
            }
        }

        node.children.filter { it.children.isNotEmpty() }.forEach { childNode ->
            val nestedStructName = childStructName(structName, childNode.name)
            emitNodeCodecFunctions(childNode, nestedStructName, "$pathPrefix.${childNode.name}")
        }
    }

    private fun PropertyInfo.valueVariantExpr(valueExpr: String): String = requiredScalar().valueExpression(valueExpr)

    private fun PropertyInfo.propertyValueType(): String = if (isStructuredMeta) {
        requiredMetaModel("host request and response types").rootStructName
    } else {
        requiredScalar().rustType
    }

    private fun PropertyInfo.getRequestVariantName(): String = "Get${name.toRustTypeName()}"

    private fun PropertyInfo.setRequestVariantName(): String = "Set${name.toRustTypeName()}"

    private fun PropertyInfo.responseVariantName(): String = name.toRustTypeName()

    private fun PropertyInfo.requestDecodeExpression(): String = if (isStructuredMeta) {
        "${requiredMetaModel("host request decoding").readFunctionName}(meta, \"$name\")"
    } else {
        "${requiredScalar().getterFunction}(meta, \"$name\")"
    }

    private fun PropertyInfo.responseInsertStatement(valueExpr: String): String = if (isStructuredMeta) {
        val model = requiredMetaModel("host response encoding")
        "insert_meta_node(&mut response_meta, \"$name\", ${model.writeFunctionName}(&$valueExpr));"
    } else {
        "insert_meta_value(&mut response_meta, \"$name\", ${valueVariantExpr(valueExpr)});"
    }

    private fun PropertyInfo.requiredMetaModel(usage: String): MetaModelInfo = requireNotNull(metaModel) {
        "Meta property '$name' requires a structured descriptor to generate $usage"
    }

    private fun PropertyInfo.requiredScalar(): RustScalar = requireNotNull(scalar) {
        "Property '$name' requires a scalar type"
    }

    private fun RustFunction.emitNoKnownFieldsError(method: HostMethod) {
        +"return Err(ProtocolError::InvalidRequest);"
    }

    private fun RustFunction.emitDecodeGetRequests(readableProperties: List<PropertyInfo>) {
        if (readableProperties.isEmpty()) {
            emitNoKnownFieldsError(HostMethod.GET)
            return
        }

        readableProperties.forEachIndexed { index, property ->
            val branchStart = if (index == 0) "if" else "else if"
            +"$branchStart meta.items.contains_key(\"${property.name}\") {"
            +"    return Ok($HOST_REQUEST_TYPE::${property.getRequestVariantName()});"
            +"}"
        }

        +"else {"
        +"    return Err(ProtocolError::InvalidRequest);"
        +"}"
    }

    private fun RustFunction.emitDecodePostRequests(writableProperties: List<PropertyInfo>) {
        if (writableProperties.isEmpty()) {
            emitNoKnownFieldsError(HostMethod.POST)
            return
        }

        writableProperties.forEachIndexed { index, property ->
            val branchStart = if (index == 0) "if" else "else if"
            +"$branchStart meta.items.contains_key(\"${property.name}\") {"
            +"    let value = match ${property.requestDecodeExpression()} {"
            +"        Ok(value) => value,"
            +"        Err(_error) => {"
            +"            return Err(ProtocolError::InvalidPropertyValue);"
            +"        }"
            +"    };"
            +"    return Ok($HOST_REQUEST_TYPE::${property.setRequestVariantName()}(value));"
            +"}"
        }

        +"else {"
        +"    return Err(ProtocolError::InvalidRequest);"
        +"}"
    }

    private fun RustFunction.emitDecodeMethodDispatch(
        readableProperties: List<PropertyInfo>,
        writableProperties: List<PropertyInfo>,
    ) {
        matchBlock("method") {
            arm(HostMethod.GET.matchPattern) {
                emitDecodeGetRequests(readableProperties)
            }
            arm(HostMethod.POST.matchPattern) {
                emitDecodePostRequests(writableProperties)
            }
            arm("_") {
                +"return Err(ProtocolError::UnknownMethod);"
            }
        }
    }

    private fun RustFile.emitDecodeHostRequest(
        readableProperties: List<PropertyInfo>,
        writableProperties: List<PropertyInfo>,
    ) {
        fn(DECODE_HOST_REQUEST_FUNCTION, args = "buffer: &[u8]", returnType = "Result<$HOST_REQUEST_TYPE, ProtocolError>", visibility = PRIVATE_VISIBILITY) {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            +"let mut decoder = PbDecoder::new(buffer);"
            ifBlock("envelope.decode(&mut decoder, buffer.len()).is_err()") {
                +"return Err(ProtocolError::DecodeRequest);"
            }

            +"let meta = &envelope.meta;"
            +"let method = match meta.items.get(\"$METHOD_FIELD\") {"
            +"    Some(m_item) => match &m_item.protoValue.value {"
            +"        Some(Value::StringValue(s)) => s.as_str(),"
            +"        Some(_) => {"
            +"            return Err(ProtocolError::InvalidMethodType);"
            +"        }"
            +"        None => {"
            +"            return Err(ProtocolError::MissingMethodValue);"
            +"        }"
            +"    },"
            +"    None => {"
            +"        return Err(ProtocolError::MissingMethod);"
            +"    }"
            +"};"

            emitDecodeMethodDispatch(readableProperties, writableProperties)
        }
    }

    private fun RustFile.emitEncodeHostResponse(
        readableProperties: List<PropertyInfo>,
        writableProperties: List<PropertyInfo>,
    ) {
        fn(
            ENCODE_HOST_RESPONSE_FUNCTION,
            args = "request: $HOST_REQUEST_TYPE, response: $HOST_RESPONSE_TYPE, output: &mut [u8]",
            returnType = "Result<usize, ProtocolError>",
            visibility = PRIVATE_VISIBILITY,
        ) {
            +"let mut response_meta: ProtoMeta = Default::default();"
            +"match (request, response) {"

            readableProperties.forEach { property ->
                +"    ($HOST_REQUEST_TYPE::${property.getRequestVariantName()}, $HOST_RESPONSE_TYPE::${property.responseVariantName()}(value)) => {"
                +"        insert_meta_value(&mut response_meta, \"$METHOD_FIELD\", ${HostMethod.GET.responseMethodValue});"
                +"        ${property.responseInsertStatement("value")}"
                +"    },"
            }

            writableProperties.forEach { property ->
                +"    ($HOST_REQUEST_TYPE::${property.setRequestVariantName()}(_), $HOST_RESPONSE_TYPE::${property.responseVariantName()}(value)) => {"
                +"        insert_meta_value(&mut response_meta, \"$METHOD_FIELD\", ${HostMethod.POST.responseMethodValue});"
                +"        ${property.responseInsertStatement("value")}"
                +"    },"
            }

            +"    _ => {"
            +"        return Err(ProtocolError::ResponseMismatch);"
            +"    },"
            +"}"
            +"write_response_message(response_meta, output)"
        }
    }

    private fun RustFile.emitTryHandleHostMessage() {
        fn(
            TRY_HANDLE_HOST_MESSAGE_FUNCTION,
            args = "buffer: &[u8], output: &mut [u8], mut on_request: impl FnMut($HOST_REQUEST_TYPE) -> Option<$HOST_RESPONSE_TYPE>",
            returnType = "Result<usize, ProtocolError>",
            visibility = PRIVATE_VISIBILITY,
        ) {
            +"let request = $DECODE_HOST_REQUEST_FUNCTION(buffer)?;"
            +"let response = match on_request(request.clone()) {"
            +"    Some(response) => response,"
            +"    None => return Ok($ERROR_CODE_MODULE::$NO_RESPONSE_CODE as usize),"
            +"};"
            +"$ENCODE_HOST_RESPONSE_FUNCTION(request, response, output)"
        }
    }

    private fun RustFile.emitHandleHostMessage() {
        fn(
            HANDLE_HOST_MESSAGE_FUNCTION,
            args = "buffer: &[u8], output: &mut [u8], on_request: impl FnMut($HOST_REQUEST_TYPE) -> Option<$HOST_RESPONSE_TYPE>",
            returnType = "isize",
            visibility = PRIVATE_VISIBILITY,
        ) {
            +"match $TRY_HANDLE_HOST_MESSAGE_FUNCTION(buffer, output, on_request) {"
            +"    Ok(written) => written as isize,"
            +"    Err(error) => protocol_error_code(error),"
            +"}"
        }
    }

    private fun RustFile.emitCompatibilityHandleMessage() {
        fn(
            HANDLE_MESSAGE_FUNCTION,
            args = "buffer: &[u8], output: &mut [u8], on_request: impl FnMut($HOST_REQUEST_TYPE) -> Option<$HOST_RESPONSE_TYPE>",
            returnType = "isize",
        ) {
            +"$HANDLE_HOST_MESSAGE_FUNCTION(buffer, output, on_request)"
        }
    }

    private fun RustFile.emitProtocolApi(context: GenerationContext) {
        emitHandleMessageReturnCodes()
        emitHostRequestEnum(context.readableProperties, context.writableProperties)
        emitHostResponseEnum(context.properties)
        emitDecodeHostRequest(context.readableProperties, context.writableProperties)
        emitEncodeHostResponse(context.readableProperties, context.writableProperties)
        emitTryHandleHostMessage()
        emitProtocolErrorCodeFunction()
        emitHandleHostMessage()
        emitCompatibilityHandleMessage()
    }

    private fun RustFile.emitSupportModuleImport(supportModuleName: String) {
        +"#[path = \"$supportModuleName.rs\"]"
        +"mod $supportModuleName;"
        +"pub use $supportModuleName::*;"
    }

    private fun RustFile.emitApiFacade(context: GenerationContext, codecModuleName: String) {
        +"#[path = \"$codecModuleName.rs\"]"
        +"mod $codecModuleName;"
        +""
        +"pub use $codecModuleName::{"
        publicHandleMessageItems.forEach { item ->
            +"    $item,"
        }
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

    public fun generateToFile(deviceSpec: DeviceSpec<*>, path: String = DEFAULT_API_PATH) {
        val apiFile = File(path)
        apiFile.parentFile?.mkdirs()

        val baseModuleName = apiFile.nameWithoutExtension.toRustFieldName()
        val codecModuleName = "$baseModuleName$CODEC_MODULE_SUFFIX"
        val supportModuleName = "$baseModuleName$SUPPORT_MODULE_SUFFIX"
        val outputDirectory = apiFile.parentFile ?: File(".")
        val codecFile = File(outputDirectory, "$codecModuleName.rs")
        val supportFile = File(outputDirectory, "$supportModuleName.rs")

        supportFile.writeText(generateDeviceSupportModule(deviceSpec))
        codecFile.writeText(generateDeviceCodecModule(deviceSpec, supportModuleName))
        apiFile.writeText(generateDeviceApiModule(deviceSpec, codecModuleName))
    }

    public fun generateToDirectory(
        deviceSpec: DeviceSpec<*>,
        directory: File,
        options: ProtocolGenerationOptions = ProtocolGenerationOptions(),
    ) {
        generate(deviceSpec, options).writeTo(directory)
    }

    override fun generate(
        deviceSpec: DeviceSpec<*>,
        options: ProtocolGenerationOptions,
    ): GeneratedProtocolPackage {
        val baseModuleName = options.moduleName.toRustFieldName()
        val codecModuleName = "$baseModuleName$CODEC_MODULE_SUFFIX"
        val supportModuleName = "$baseModuleName$SUPPORT_MODULE_SUFFIX"
        val rustOptions = options.rustBackendOptions()
        val protoFiles = rustOptions.protoSources.loadProtoFiles()
        val sourceFiles = generateRustSourceFiles(
            deviceSpec = deviceSpec,
            baseModuleName = baseModuleName,
            codecModuleName = codecModuleName,
            supportModuleName = supportModuleName,
            runtime = rustOptions.runtime,
        )
        val files = when (options.delivery) {
            ProtocolDelivery.SOURCE_FILES -> protoFiles + sourceFiles
            ProtocolDelivery.LIBRARY_PACKAGE -> generateRustCrateFiles(
                deviceSpec = deviceSpec,
                baseModuleName = baseModuleName,
                sourceFiles = sourceFiles,
                protoFiles = protoFiles,
                crateName = (options.packageName ?: "${baseModuleName}_protocol").toRustPackageName(),
                runtime = rustOptions.runtime,
                generateBuildScript = rustOptions.generateBuildScript ?: true,
            )
        }
        return GeneratedProtocolPackage(
            language = language,
            files = files,
        )
    }

    private fun ProtocolGenerationOptions.rustBackendOptions(): RustBackendOptions = when (val backendOptions = backend) {
        is RustBackendOptions -> backendOptions
        DefaultProtocolBackendOptions -> RustBackendOptions()
        else -> error("Rust generator does not accept backend options: ${backendOptions::class.simpleName}")
    }

    private fun List<ProtocolProtoSource>.loadProtoFiles(): List<GeneratedProtocolFile> =
        map { protoSource -> protoSource.load() }

    private fun generateRustSourceFiles(
        deviceSpec: DeviceSpec<*>,
        baseModuleName: String,
        codecModuleName: String,
        supportModuleName: String,
        runtime: RustProtocolRuntime,
    ): List<GeneratedProtocolFile> = listOf(
        GeneratedProtocolFile(
            relativePath = "$baseModuleName.rs",
            content = generateDeviceApiModule(deviceSpec, codecModuleName),
        ),
        GeneratedProtocolFile(
            relativePath = "$codecModuleName.rs",
            content = generateDeviceCodecModule(
                deviceSpec = deviceSpec,
                supportModuleName = supportModuleName,
                runtime = runtime,
            ),
        ),
        GeneratedProtocolFile(
            relativePath = "$supportModuleName.rs",
            content = generateDeviceSupportModule(deviceSpec),
        ),
    )

    private fun generateRustCrateFiles(
        deviceSpec: DeviceSpec<*>,
        baseModuleName: String,
        sourceFiles: List<GeneratedProtocolFile>,
        protoFiles: List<GeneratedProtocolFile>,
        crateName: String,
        runtime: RustProtocolRuntime,
        generateBuildScript: Boolean,
    ): List<GeneratedProtocolFile> = listOf(
        GeneratedProtocolFile(
            relativePath = CRATE_MANIFEST_PATH,
            content = generateRustCrateManifest(
                crateName = crateName,
                runtime = runtime,
                generateBuildScript = runtime.shouldGenerateBuildScript(generateBuildScript, protoFiles),
            ),
        ),
    ) + buildScriptFile(runtime, protoFiles, generateBuildScript) + protoFiles + listOf(
        GeneratedProtocolFile(
            relativePath = "$CRATE_SOURCE_ROOT/lib.rs",
            content = generateRustCrateRoot(
                codecModuleName = "$baseModuleName$CODEC_MODULE_SUFFIX",
                metaModels = deviceSpec.rustGenerationContext().metaModels,
            ),
        ),
    ) + sourceFiles.filterNot { sourceFile ->
        sourceFile.relativePath == "$baseModuleName.rs"
    }.map { sourceFile ->
        sourceFile.copy(relativePath = "$CRATE_SOURCE_ROOT/${sourceFile.relativePath}")
    } + GeneratedProtocolFile(
        relativePath = CRATE_README_PATH,
        content = generateRustCrateReadme(
            deviceSpec = deviceSpec,
            crateName = crateName,
            baseModuleName = baseModuleName,
            runtime = runtime,
            protoFiles = protoFiles,
            buildScriptGenerated = runtime.shouldGenerateBuildScript(generateBuildScript, protoFiles),
        ),
    )

    private fun buildScriptFile(
        runtime: RustProtocolRuntime,
        protoFiles: List<GeneratedProtocolFile>,
        generateBuildScript: Boolean,
    ): List<GeneratedProtocolFile> {
        if (!runtime.shouldGenerateBuildScript(generateBuildScript, protoFiles)) return emptyList()
        val outDirModule = requireNotNull(runtime.outDirModule)
        return listOf(
            GeneratedProtocolFile(
                relativePath = CRATE_BUILD_SCRIPT_PATH,
                content = generateRustCrateBuildScript(
                    protoFiles = protoFiles,
                    outDirModule = outDirModule,
                ),
            ),
        )
    }

    private fun RustProtocolRuntime.shouldGenerateBuildScript(
        generateBuildScript: Boolean,
        protoFiles: List<GeneratedProtocolFile>,
    ): Boolean = generateBuildScript && outDirModule != null && protoFiles.isNotEmpty()

    private fun generateRustCrateBuildScript(
        protoFiles: List<GeneratedProtocolFile>,
        outDirModule: RustOutDirProtoModule,
    ): String {
        val protoList = protoFiles.joinToString(separator = "\n") { protoFile ->
            "    \"${protoFile.relativePath}\","
        }
        return """
use std::path::PathBuf;

const PROTO_FILES: &[&str] = &[
$protoList
];

fn main() {
    for proto_file in PROTO_FILES {
        println!("cargo:rerun-if-changed={}", proto_file);
    }

    let out_dir = PathBuf::from(std::env::var("OUT_DIR").expect("OUT_DIR is not set by Cargo"));
    let mut generator = micropb_gen::Generator::new();
    generator.use_container_alloc();
    generator
        .compile_protos(PROTO_FILES, out_dir.join("${outDirModule.generatedFileName}"))
        .expect("failed to generate Rust protobuf bindings");
}
""".trimIndent() + "\n"
    }

    private fun generateRustCrateManifest(
        crateName: String,
        runtime: RustProtocolRuntime,
        generateBuildScript: Boolean,
    ): String {
        val runtimeDependency = runtime.cargoDependency?.toCargoDependencyLine()
        val runtimeDependencyBlock = if (runtimeDependency == null) "" else "$runtimeDependency\n"
        val buildScriptLine = if (generateBuildScript) """build = "build.rs"""" else ""
        val buildDependenciesBlock = if (generateBuildScript) """

[build-dependencies]
micropb-gen = "*"
""".trimIndent() else ""
        return """
# Generated by controls-proto. Do not edit by hand.

[package]
name = "$crateName"
version = "0.1.0"
edition = "2021"
publish = false
$buildScriptLine

[lib]
name = "$crateName"
path = "src/lib.rs"

[dependencies]
micropb = { version = "*", features = ["alloc"] }
${runtimeDependencyBlock.trimEnd()}
$buildDependenciesBlock
""".trimIndent() + "\n"
    }

    private fun RustCargoDependency.toCargoDependencyLine(): String {
        val crate = crateName.toRustPackageName()
        val dependencyProperties = listOfNotNull(
            version?.let { "version = \"$it\"" },
            path?.let { "path = \"$it\"" },
        )
        return if (dependencyProperties.isEmpty()) {
            "$crate = \"*\""
        } else {
            "$crate = { ${dependencyProperties.joinToString(separator = ", ")} }"
        }
    }

    private fun generateRustCrateRoot(
        codecModuleName: String,
        metaModels: List<MetaModelInfo>,
    ): String {
        val metaModuleExports = if (metaModels.isEmpty()) "" else "\n\n" + """
pub use $codecModuleName::{
${metaModels.joinToString(separator = ",\n") { "    ${it.moduleName}" }},
};
""".trimIndent()

        return """
#![no_std]

extern crate alloc;

#[path = "$codecModuleName.rs"]
mod $codecModuleName;

pub use $codecModuleName::{
${publicHandleMessageItems.joinToString(separator = ",\n") { "    $it" }},
};
""".trimIndent() + metaModuleExports + "\n"
    }

    private fun generateRustCrateReadme(
        deviceSpec: DeviceSpec<*>,
        crateName: String,
        baseModuleName: String,
        runtime: RustProtocolRuntime,
        protoFiles: List<GeneratedProtocolFile>,
        buildScriptGenerated: Boolean,
    ): String {
        val context = deviceSpec.rustGenerationContext()
        val requestVariants = (context.readableProperties.map { it.getRequestVariantName() } +
            context.writableProperties.map { it.setRequestVariantName() })
            .joinToString(separator = ", ")
            .ifBlank { "none" }
        val installedProtoFiles = protoFiles.joinToString(separator = "\n") { protoFile ->
            "- `${protoFile.relativePath}`"
        }.ifBlank { "- none" }
        val runtimeBuildNote = runtime.outDirModule?.let { outDirModule ->
            val buildOwnerNote = if (buildScriptGenerated) {
                "This package includes `build.rs`, so it generates `OUT_DIR/${outDirModule.generatedFileName}` when Cargo builds the crate."
            } else {
                "The MCU crate's own `build.rs` should generate `OUT_DIR/${outDirModule.generatedFileName}` from the installed proto files."
            }
            """
This generated code includes:

```rust
mod ${outDirModule.moduleName} {
    include!(concat!(env!("OUT_DIR"), "/${outDirModule.generatedFileName}"));
}
```

$buildOwnerNote
""".trimIndent()
        } ?: """
The MCU project should provide this runtime module before compiling the generated protocol code.
""".trimIndent()
        return """
# $crateName

Generated Rust protocol crate for host-to-device messages.

This crate installs protocol schema files:

$installedProtoFiles

It does not generate or own MCU-specific linker/build behavior.
Any generated `build.rs` only compiles installed proto files into Rust protobuf types.

$runtimeBuildNote

The generated Rust code expects protobuf runtime types at:

```rust
${runtime.protoModulePath}
```

Typically that runtime crate/module is produced from the installed DataForge proto files in the MCU repository.
If this generated package is used as a standalone Cargo dependency, configure `RustProtocolRuntime.externalCrate(...)`
so `Cargo.toml` contains the real protobuf-runtime crate dependency.

## Use From Another Crate

```toml
[dependencies]
$crateName = { path = "path/to/$crateName" }
```

```rust
use $crateName::{handle_message, HostRequest, HostResponse};

let written = handle_message(input_bytes, output_bytes, |request| {
    match request {
        // Return Some(HostResponse::...) for requests you want to answer.
        _ => None,
    }
});

if written > 0 {
    let response = &output_bytes[..written as usize];
    // Send response to the host here.
}
```

`handle_message` returns a C-style signed value: `> 0` is response byte count,
`error_code::NO_RESPONSE` is no response, and `< 0` is a protocol error code.
You can ignore exact error codes in normal code, or compare with `error_code::*`
constants when debugging.

The public API is `handle_message`, `HostRequest`, `HostResponse`, and the `error_code` module.
Generated structured-meta modules, if any, are re-exported from the crate root.

Known request variants: $requestVariants.
""".trimIndent() + "\n"
    }

    public fun generateDeviceApiModule(
        deviceSpec: DeviceSpec<*>,
        codecModuleName: String = DEFAULT_CODEC_MODULE,
    ): String {
        val context = deviceSpec.rustGenerationContext()
        return rustFile {
            emitApiFacade(context, codecModuleName)
        }.toString()
    }

    public fun generateDeviceSupportModule(deviceSpec: DeviceSpec<*>): String {
        val context = deviceSpec.rustGenerationContext()
        return rustFile {
            +"#![allow(dead_code)]"
            use("super::*")
            emitSupportDefinitions(context.metaModels)
        }.toString()
    }

    public fun generateDeviceCodecModule(
        deviceSpec: DeviceSpec<*>,
        supportModuleName: String = DEFAULT_SUPPORT_MODULE,
        runtime: RustProtocolRuntime = RustProtocolRuntime.outDirModule(),
    ): String {
        val context = deviceSpec.rustGenerationContext()
        return rustFile {
            emitRuntimePrelude(runtime)
            emitProtocolErrorType()
            emitSupportModuleImport(supportModuleName)
            emitProtocolApi(context)
        }.toString()
    }

    @Deprecated("Use generateDeviceCodecModule(deviceSpec, supportModuleName) instead.")
    public fun generateSplitDeviceHandler(
        deviceSpec: DeviceSpec<*>,
        supportModuleName: String = DEFAULT_SUPPORT_MODULE,
    ): String = generateDeviceCodecModule(deviceSpec, supportModuleName)

    public fun generateDeviceHandler(deviceSpec: DeviceSpec<*>): String {
        val context = deviceSpec.rustGenerationContext()
        return rustFile {
            emitRuntimePrelude(RustProtocolRuntime.outDirModule())
            emitProtocolErrorType()
            emitSupportDefinitions(context.metaModels)
            emitProtocolApi(context)
        }.toString()
    }

    @Deprecated("Use generateDeviceHandler(deviceSpec) with an explicit DeviceSpec instead.")
    public fun generateRust(): String = generateDeviceHandler(object : DeviceSpec<Device>() {})

    @JvmStatic
    public fun main(args: Array<String>) {
        // No-op
    }
}

@Deprecated("Use RustProtocolGenerator or ProtocolGenerators.rust instead.")
public object MetaRustGenerator : ProtocolGenerator {
    override val language: ProtocolLanguage get() = RustProtocolGenerator.language

    override fun generate(
        deviceSpec: DeviceSpec<*>,
        options: ProtocolGenerationOptions,
    ): GeneratedProtocolPackage = RustProtocolGenerator.generate(deviceSpec, options)

    public fun generateToFile(deviceSpec: DeviceSpec<*>, path: String = "build/generated/rust/device.rs") {
        RustProtocolGenerator.generateToFile(deviceSpec, path)
    }

    public fun generateToDirectory(
        deviceSpec: DeviceSpec<*>,
        directory: File,
        options: ProtocolGenerationOptions = ProtocolGenerationOptions(),
    ) {
        RustProtocolGenerator.generateToDirectory(deviceSpec, directory, options)
    }

    public fun generateDeviceApiModule(
        deviceSpec: DeviceSpec<*>,
        codecModuleName: String = "device_codec",
    ): String = RustProtocolGenerator.generateDeviceApiModule(deviceSpec, codecModuleName)

    public fun generateDeviceSupportModule(deviceSpec: DeviceSpec<*>): String =
        RustProtocolGenerator.generateDeviceSupportModule(deviceSpec)

    public fun generateDeviceCodecModule(
        deviceSpec: DeviceSpec<*>,
        supportModuleName: String = "device_support",
    ): String = RustProtocolGenerator.generateDeviceCodecModule(deviceSpec, supportModuleName)

    @Deprecated("Use RustProtocolGenerator.generateDeviceCodecModule(deviceSpec, supportModuleName) instead.")
    public fun generateSplitDeviceHandler(
        deviceSpec: DeviceSpec<*>,
        supportModuleName: String = "device_support",
    ): String = RustProtocolGenerator.generateDeviceCodecModule(deviceSpec, supportModuleName)

    public fun generateDeviceHandler(deviceSpec: DeviceSpec<*>): String =
        RustProtocolGenerator.generateDeviceHandler(deviceSpec)

    @Deprecated("Use RustProtocolGenerator.generateDeviceHandler(deviceSpec) with an explicit DeviceSpec instead.")
    public fun generateRust(): String = RustProtocolGenerator.generateDeviceHandler(object : DeviceSpec<Device>() {})

    @JvmStatic
    public fun main(args: Array<String>) {
        RustProtocolGenerator.main(args)
    }
}
