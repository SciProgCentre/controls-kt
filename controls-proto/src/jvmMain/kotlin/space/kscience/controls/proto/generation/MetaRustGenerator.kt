package space.kscience.controls.proto.generation

import space.kscience.controls.spec.*
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.ValueType
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.meta.MetaConverter
import java.io.File

object MetaRustGenerator {

    fun generateToFile(deviceSpec: DeviceSpec<*>, path: String = "build/generated/rust/device.rs") {
        val file = File(path)
        file.parentFile.mkdirs()
        file.writeText(generateDeviceHandler(deviceSpec))
    }

    fun generateDeviceHandler(deviceSpec: DeviceSpec<*>): String = rustFile {
        use("alloc::vec::Vec")
        use("micropb::{MessageDecode, PbDecoder}")

        // Include the generated protobuf code
        +"mod proto {"
        +"    #![allow(clippy::all)]"
        +"    #![allow(nonstandard_style, unused, irrefutable_let_patterns)]"
        +"    include!(concat!(env!(\"OUT_DIR\"), \"/meta.rs\"));"
        +"}"
        
        // Import the generated types
        use("proto::space_::kscience_::dataforge_::io_::proto_::{ProtoMeta, ProtoEnvelope}")
        use("proto::space_::kscience_::dataforge_::io_::proto_::ProtoMeta_::ProtoValue_::Value")


        // Helper functions
        fn("update_i32", args = "data: &[u8], name: &str", returnType = "()") {
            +"if data.len() == 4 {"
            +"    let val_ = i32::from_le_bytes(data[0..4].try_into().unwrap());"
            +"    defmt::info!(\"\\nPOST request for property '{}' (i32) with value: {}\", name, val_);"
            +"} else {"
            +"    defmt::warn!(\"Mismatch data length for i32 property '{}': expect 4, got {}\", name, data.len());"
            +"}"
        }

        fn("update_i64", args = "data: &[u8], name: &str", returnType = "()") {
            +"if data.len() == 8 {"
            +"    let val_ = i64::from_le_bytes(data[0..8].try_into().unwrap());"
            +"    defmt::info!(\"\\nPOST request for property '{}' (i64) with value: {}\", name, val_);"
            +"} else {"
            +"     defmt::warn!(\"Mismatch data length for i64 property '{}': expect 8, got {}\", name, data.len());"
            +"}"
        }

        fn("update_f32", args = "data: &[u8], name: &str", returnType = "()") {
            +"if data.len() == 4 {"
            +"    let val_ = f32::from_le_bytes(data[0..4].try_into().unwrap());"
            +"    defmt::info!(\"\\nPOST request for property '{}' (f32) with value: {}\", name, val_);"
            +"} else {"
            +"     defmt::warn!(\"Mismatch data length for f32 property '{}': expect 4, got {}\", name, data.len());"
            +"}"
        }

        fn("update_f64", args = "data: &[u8], name: &str", returnType = "()") {
            +"if data.len() == 8 {"
            +"    let val_ = f64::from_le_bytes(data[0..8].try_into().unwrap());"
            +"    defmt::info!(\"\\nPOST request for property '{}' (f64) with value: {}\", name, val_);"
            +"} else {"
            +"    defmt::warn!(\"Mismatch data length for f64 property '{}': expect 8, got {}\", name, data.len());"
            +"}"
        }
        
        fn("update_string", args = "data: &[u8], name: &str", returnType = "()") {
            +"if let Ok(s) = core::str::from_utf8(data) {"
            +"    defmt::info!(\"\\nPOST request for property '{}' (string) with value: {}\", name, s);"
            +"} else {"
            +"    defmt::warn!(\"Invalid UTF-8 for property '{}'\", name);"
            +"}"
        }


        // Main task function
        // User requested ONLY the handler, no RTIC function.
        fn("handle_message", 
           args = "buffer: &[u8]", 
           returnType = "()"
        ) {
            +"let mut envelope: ProtoEnvelope = Default::default();"
            // Using PbDecoder as requested
            +"let mut decoder = PbDecoder::new(buffer);"
            +"if envelope.decode(&mut decoder, buffer.len()).is_err() {"
            +"    defmt::error!(\"Failed to decode ProtoEnvelope\");"
            +"    return;"
            +"}"
            
            // Using & for field access as seen in snippet
            +"let meta = &envelope.meta;"
            +"let data = &envelope.dataBytes;"

            +"let method = if let Some(m_item) = meta.items.get(\"method\") {"
            +"     if let Some(Value::StringValue(s)) = &m_item.protoValue.value {"
            +"         s.as_str()"
            +"     } else {"
            +"         \"POST\""
            +"     }"
            +"} else {"
            +"    \"POST\""
            +"};"

            +"match method {"
            +"    \"GET\" => {"
                      // readable properties
                      deviceSpec.properties.values.filter { it.descriptor.readable && !it.name.startsWith("@") }.forEach { prop ->
                          +"        if meta.items.contains_key(\"${prop.name}\") {"
                          +"            defmt::info!(\"\\nGET request for property '${prop.name}' - (read simulation)\");"
                          +"        }"
                      }
            +"    },"
            +"    \"POST\" => {"

            // Standard Meta property updates
            deviceSpec.properties.values.filter { it.descriptor.mutable && !it.name.startsWith("@") }.forEach { prop ->
                // Try to get explicit Rust type from attributes
                val attributes = prop.descriptor.metaDescriptor.attributes 
                var rustType = attributes["rust_type"].string
                
                // Fallback: infer from property converter
                if (rustType == null) {
                    rustType = when (prop.converter) {
                        MetaConverter.double -> "f64"
                        MetaConverter.string -> "string"
                        else -> null
                    }
                }

                +"        if meta.items.contains_key(\"${prop.name}\") {"
                if (rustType != null) {
                    when (rustType) {
                        "int", "i32" -> {
                             +"            update_i32(data, \"${prop.name}\");"
                        }
                        "long", "i64" -> {
                             +"            update_i64(data, \"${prop.name}\");"
                        }
                        "float", "f32" -> {
                             +"            update_f32(data, \"${prop.name}\");"
                        }
                        "double", "f64" -> {
                             +"            update_f64(data, \"${prop.name}\");"
                        }
                         "string", "utf8" -> {
                             +"            update_string(data, \"${prop.name}\");"
                        }
                        else -> {
                             +"            // Unknown rust_type: $rustType"
                        }
                    }
                } else {
                     +"            // Generic handling if type not known"
                     +"            if let Some(type_item) = meta.items.get(\"type\") {"
                     +"                 if let Some(Value::StringValue(type_str)) = &type_item.protoValue.value {"
                     +"                     defmt::info!(\"\\nGeneric POST type: {}\", type_str.as_str());"
                     +"                 }"
                     +"            }"
                }
                +"        }"
            }
            +"    },"
            +"    _ => {"
            +"        defmt::warn!(\"Unknown method: {}\", method);"
            +"    }"
            +"}"
        }
    }.toString()
    
    // Kept for compatibility
    fun generateRust(): String = "" 

    @JvmStatic
    fun main(args: Array<String>) {
         // No-op
    }
}
