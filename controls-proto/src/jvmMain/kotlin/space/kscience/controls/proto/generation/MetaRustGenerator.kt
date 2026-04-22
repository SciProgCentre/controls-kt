package space.kscience.controls.proto.generation

import space.kscience.controls.api.Device
import space.kscience.controls.spec.*
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import java.io.File


public object MetaRustGenerator {

    private data class PropertyInfo(
        val name: String,
        val rustType: String,
        val readable: Boolean,
        val mutable: Boolean,
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
            PropertyInfo(
                name = prop.name,
                rustType = resolvedRustType,
                readable = prop.descriptor.readable,
                mutable = prop.descriptor.mutable,
            )
        }

    private fun RustFile.emitCodecHelpers() {
        fn("insert_meta_value", args = "meta: &mut ProtoMeta, key: &str, value: Value", returnType = "()") {
            +"let mut item: ProtoMeta = Default::default();"
            +"item.set_protoValue(ProtoValue {"
            +"    r#value: Some(value),"
            +"});"
            +"meta.items.insert(key.to_string(), item);"
        }

        fn("build_response_message", args = "response_meta: ProtoMeta", returnType = "Option<Vec<u8>>") {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            +"envelope.set_meta(response_meta);"
            +"envelope.r#dataBytes = Vec::new();"
            +""
            +"let mut encoded = Vec::with_capacity(envelope.compute_size());"
            +"let mut encoder = PbEncoder::new(&mut encoded);"
            +"if envelope.encode(&mut encoder).is_err() {"
            +"    defmt::error!(\"\\nFailed to serialize GET response\");"
            +"    return None;"
            +"}"
            +"Some(encoded)"
        }

        fn("get_meta_i32", args = "meta: &ProtoMeta, key: &str", returnType = "Result<i32, &'static str>") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::Int32Value(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected Int32Value\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_i64", args = "meta: &ProtoMeta, key: &str", returnType = "Result<i64, &'static str>") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::Int64Value(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected Int64Value\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_f32", args = "meta: &ProtoMeta, key: &str", returnType = "Result<f32, &'static str>") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::FloatValue(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected FloatValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_f64", args = "meta: &ProtoMeta, key: &str", returnType = "Result<f64, &'static str>") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::DoubleValue(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected DoubleValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_bool", args = "meta: &ProtoMeta, key: &str", returnType = "Result<bool, &'static str>") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::BooleanValue(value)) => Ok(*value),"
            +"        Some(_) => Err(\"Type mismatch: expected BooleanValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_string", args = "meta: &ProtoMeta, key: &str", returnType = "Result<String, &'static str>") {
            +"match meta.items.get(key) {"
            +"    Some(item) => match &item.protoValue.value {"
            +"        Some(Value::StringValue(value)) => Ok(value.clone()),"
            +"        Some(_) => Err(\"Type mismatch: expected StringValue\"),"
            +"        None => Err(\"Missing value\"),"
            +"    },"
            +"    None => Err(\"Missing field\"),"
            +"}"
        }

        fn("get_meta_present", args = "meta: &ProtoMeta, key: &str", returnType = "Result<(), &'static str>") {
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

    private fun PropertyInfo.getMatcherArm(): String = when (rustType) {
        "int", "i32" -> "Some(Value::Int32Value(raw_value))"
        "long", "i64" -> "Some(Value::Int64Value(raw_value))"
        "float", "f32" -> "Some(Value::FloatValue(raw_value))"
        "double", "f64" -> "Some(Value::DoubleValue(raw_value))"
        "string", "utf8" -> "Some(Value::StringValue(raw_value))"
        "bool", "boolean" -> "Some(Value::BooleanValue(raw_value))"
        "meta" -> "Some(_raw_value)"
        else -> "Some(_)"
    }

    private fun PropertyInfo.getValueExtraction(): String = when (rustType) {
        "string", "utf8" -> "String::from(raw_value.as_str())"
        "meta" -> "String::from(\"meta\")"
        else -> "*raw_value"
    }

    private fun PropertyInfo.getReadSimulationExpression(): String = when (rustType) {
        "int", "i32" -> "7i32"
        "long", "i64" -> "7i64"
        "float", "f32" -> "1.23f32"
        "double", "f64" -> "24.5f64"
        "string", "utf8" -> "String::from(\"demo-value\")"
        "bool", "boolean" -> "true"
        "meta" -> "String::from(\"meta\")"
        else -> "0i32"
    }

    private fun PropertyInfo.postGetterFunction(): String = when (rustType) {
        "int", "i32" -> "get_meta_i32"
        "long", "i64" -> "get_meta_i64"
        "float", "f32" -> "get_meta_f32"
        "double", "f64" -> "get_meta_f64"
        "string", "utf8" -> "get_meta_string"
        "bool", "boolean" -> "get_meta_bool"
        "meta" -> "get_meta_present"
        else -> "get_meta_present"
    }

    private fun RustFunction.emitGetHandlers(readableProperties: List<PropertyInfo>) {
        if (readableProperties.isEmpty()) {
            +"defmt::warn!(\"\\nGET request has no known fields to respond with\");"
            +"return None;"
            return
        }

        readableProperties.forEachIndexed { index, property ->
            val branchStart = if (index == 0) "if" else "else if"
            +"$branchStart meta.items.contains_key(\"${property.name}\") {"
            +"    defmt::info!(\"\\nGET request for property '${property.name}' - (read simulation)\");"
            +"    let value = ${property.getReadSimulationExpression()};"
            +"    /*USER CODE*/"
            +"    insert_meta_value(&mut response_meta, \"${property.name}\", ${property.valueVariantExpr("value")});"
            +"    return build_response_message(response_meta);"
            +"}"
        }

        +"else {"
        +"    defmt::warn!(\"\\nGET request has no known fields to respond with\");"
        +"    return None;"
        +"}"
    }

    private fun RustFunction.emitPostHandlers(mutableProperties: List<PropertyInfo>) {
        if (mutableProperties.isEmpty()) {
            +"defmt::warn!(\"\\nPOST request has no known fields to apply\");"
            +"return None;"
            return
        }

        mutableProperties.forEachIndexed { index, property ->
            val branchStart = if (index == 0) "if" else "else if"
            +"$branchStart meta.items.contains_key(\"${property.name}\") {"
            if (property.rustType == "meta") {
                +"    match ${property.postGetterFunction()}(meta, \"${property.name}\") {"
                +"        Ok(()) => {"
                +"            defmt::info!(\"\\nPOST request for property '${property.name}' (meta)\");"
                +"            /*USER CODE*/"
                +"            insert_meta_value(&mut response_meta, \"${property.name}\", ${property.valueVariantExpr("String::from(\"meta\")")});"
                +"            return build_response_message(response_meta);"
                +"        }"
                +"        Err(error) => {"
                +"            defmt::warn!(\"\\nPOST decode error for '${property.name}': {}\", error);"
                +"            return None;"
                +"        }"
                +"    }"
            } else {
                +"    match ${property.postGetterFunction()}(meta, \"${property.name}\") {"
                +"        Ok(value) => {"
                +"            defmt::info!(\"\\nPOST request for property '${property.name}' (${property.rustType}) with value: {}\", value);"
                +"            /*USER CODE*/"
                +"            insert_meta_value(&mut response_meta, \"${property.name}\", ${property.valueVariantExpr("value")});"
                +"            return build_response_message(response_meta);"
                +"        }"
                +"        Err(error) => {"
                +"            defmt::warn!(\"\\nPOST decode error for '${property.name}': {}\", error);"
                +"            return None;"
                +"        }"
                +"    }"
            }
            +"}"
        }

        +"else {"
        +"    defmt::warn!(\"\\nPOST request has no known fields to apply\");"
        +"    return None;"
        +"}"
    }

    private fun RustFunction.emitMethodDispatch(
        readableProperties: List<PropertyInfo>,
        mutableProperties: List<PropertyInfo>,
    ) {
        matchBlock("method") {
            arm("\"GET\"") {
                +"let mut response_meta: ProtoMeta = Default::default();"
                +"insert_meta_value(&mut response_meta, \"method\", Value::StringValue(String::from(\"GET\")));"
                emitGetHandlers(readableProperties)
            }
            arm("\"POST\"") {
                +"let mut response_meta: ProtoMeta = Default::default();"
                +"insert_meta_value(&mut response_meta, \"method\", Value::StringValue(String::from(\"POST\")));"
                emitPostHandlers(mutableProperties)
            }
            arm("_") {
                +"defmt::warn!(\"\\nUnknown method: {} (expected GET or POST)\", method);"
                +"return None;"
            }
        }
    }

    public fun generateToFile(deviceSpec: DeviceSpec<*>, path: String = "build/generated/rust/device.rs") {
        val file = File(path)
        file.parentFile.mkdirs()
        file.writeText(generateDeviceHandler(deviceSpec))
    }

    public fun generateDeviceHandler(deviceSpec: DeviceSpec<*>): String = rustFile {
        val properties = deviceSpec.propertyInfos()
        val readableProperties = properties.filter { it.readable }
        val mutableProperties = properties.filter { it.mutable }
        use("alloc::vec::Vec")
        use("alloc::string::{String, ToString}")
        use("micropb::{MessageDecode, MessageEncode, PbDecoder, PbEncoder}")

        // Include the generated protobuf code
        +"mod proto {"
        +"    #![allow(clippy::all)]"
        +"    #![allow(nonstandard_style, unused, irrefutable_let_patterns)]"
        +"    include!(concat!(env!(\"OUT_DIR\"), \"/meta.rs\"));"
        +"}"
        
        // Import the generated types
        use("proto::space_::kscience_::dataforge_::io_::proto_::{ProtoMeta, ProtoEnvelope}")
        use("proto::space_::kscience_::dataforge_::io_::proto_::ProtoMeta_::ProtoValue")
        use("proto::space_::kscience_::dataforge_::io_::proto_::ProtoMeta_::ProtoValue_::Value")

        emitCodecHelpers()

        // Main task function
        fn("handle_message", 
           args = "buffer: &[u8]", 
           returnType = "Option<Vec<u8>>"
        ) {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            +"let mut decoder = PbDecoder::new(buffer);"
            ifBlock("envelope.decode(&mut decoder, buffer.len()).is_err()") {
                +"defmt::error!(\"\\nFailed to decode ProtoEnvelope\");"
                +"return None;"
            }
            +"defmt::info!(\"\\nDeserialized {} bytes\", buffer.len());"

            +"let meta = &envelope.meta;"
            +"let method = match meta.items.get(\"method\") {"
            +"    Some(m_item) => match &m_item.protoValue.value {"
            +"        Some(Value::StringValue(s)) => s.as_str(),"
            +"        Some(_) => {"
            +"            defmt::error!(\"\\nInvalid 'method' type: expected string ('GET' or 'POST')\");"
            +"            return None;"
            +"        }"
            +"        None => {"
            +"            defmt::error!(\"\\nMissing 'method' value\");"
            +"            return None;"
            +"        }"
            +"    },"
            +"    None => {"
            +"        defmt::error!(\"\\nMissing 'method' field\");"
            +"        return None;"
            +"    }"
            +"};"

            emitMethodDispatch(readableProperties, mutableProperties)
        }
    }.toString()
    
    // Kept for compatibility
    public fun generateRust(): String = generateDeviceHandler(object : DeviceSpec<Device>() {})

    @JvmStatic
    public fun main(args: Array<String>) {
         // No-op
    }
}
