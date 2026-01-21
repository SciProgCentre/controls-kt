package space.kscience.controls.proto.generation

import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratorTest {
    @Test
    fun generateAndSave() {
        val code = MetaRustGenerator.generateRust()
        val file = java.io.File("meta_decoder.rs")
        file.writeText(code)
        println("Saved generated Rust code to ${file.absolutePath}")
    }

    @Test
    fun testRustGeneration() {
        val code = MetaRustGenerator.generateRust()
        
        // Check for presence of new types in Visitor trait
        assertTrue(code.contains("fn on_float"), "Should contain on_float")
        assertTrue(code.contains("fn on_int64"), "Should contain on_int64")
        assertTrue(code.contains("fn on_bytes"), "Should contain on_bytes")
        
        // Check for handling of list logic (checking for iteration loop)
        assertTrue(code.contains("for (i, v) in l.values.iter().enumerate()"), "Should handle list iteration")
        assertTrue(code.contains("for (i, v) in l.values.iter().enumerate()"), "Should handle Float64List iteration")
        
        println("Generated code snippet:\n${code.take(500)}...")
    }
}
