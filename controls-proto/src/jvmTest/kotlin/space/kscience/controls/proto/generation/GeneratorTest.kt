package space.kscience.controls.proto.generation

import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratorTest {
    @Test
    fun generateAndSave() {
        val code = MetaRustGenerator.generateRust()
        val file = kotlin.io.path.createTempFile(prefix = "meta_decoder_", suffix = ".rs").toFile()
        file.deleteOnExit()
        file.writeText(code)
        println("Saved generated Rust code to ${file.absolutePath}")
    }

    @Test
    fun testRustGeneration() {
        val code = MetaRustGenerator.generateRust()

        assertTrue(code.contains("use micropb::{MessageDecode, MessageEncode, PbDecoder, PbEncoder}"), "Should import micropb codecs")
        assertTrue(code.contains("fn insert_meta_value"), "Should contain meta insert helper")
        assertTrue(code.contains("fn build_response_message"), "Should contain response builder helper")
        assertTrue(code.contains("envelope.r#dataBytes = Vec::new();"), "Response data field should stay empty")
        assertTrue(code.contains("fn handle_message"), "Should contain main message handler")
        assertTrue(code.contains("Option<Vec<u8>>"), "Handler should return optional response packet")
        assertTrue(code.contains("let mut envelope: ProtoEnvelope"), "Should decode protobuf envelope")
        assertTrue(code.contains("match method"), "Should dispatch by request method")
        assertTrue(code.contains("POST request has no known fields to apply") || code.contains("meta.items.get("), "Should include POST meta-based handling path")
        
        println("Generated code snippet:\n${code.take(500)}...")
    }
}
