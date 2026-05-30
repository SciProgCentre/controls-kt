package space.kscience.controls.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.io.asBinary
import space.kscience.dataforge.io.io
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.parseAsName
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileEnvelopeOperationsTest {

    @TempDir
    lateinit var tempDir: Path

    val context = Context("test"){
        plugin(IOPlugin)
    }
    val ioPlugin = context.io

    @Test
    fun testSingleFileEnvelopeOperations() {
        val operations = SingleFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(Meta { "key" put "value" }, null)
        val name = "test".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)
        
        val iterated = operations.iterate(tempDir).toList()
        assertEquals(1, iterated.size, "Should find one envelope")
        assertEquals(name, iterated[0].first)
    }

    @Test
    fun testNativeFileEnvelopeOperations() {
        val operations = NativeFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(
            Meta { "key" put "value" },
            "Hello".toByteArray().asBinary()
        )
        val name = "test".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)

        val iterated = operations.iterate(tempDir).toList()
        assertEquals(1, iterated.size, "Should find one envelope")
        assertEquals(name, iterated[0].first)
    }

    @Test
    fun testNativeReadSingleFile() {
        // First write a single file envelope using SingleFileEnvelopeOperations
        val singleOps = SingleFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(Meta { "key" put "value" }, null)
        val name = "single".parseAsName()
        singleOps.writeEnvelope(name, tempDir, envelope)

        // Then try to read it using NativeFileEnvelopeOperations
        val nativeOps = NativeFileEnvelopeOperations(ioPlugin)
        val iterated = nativeOps.iterate(tempDir).toList()
        
        assertTrue(iterated.any { it.first == name }, "Native operations should be able to read single file envelope")
    }
    
    @Test
    fun testNameWithDots() {
        val operations = SingleFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(Meta { "key" put "value" }, null)
        val name = "test.name".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)

        val iterated = operations.iterate(tempDir).toList()
        assertEquals(1, iterated.size)
        assertEquals(name, iterated[0].first)
    }
}
