package space.kscience.controls.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.io.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import kotlinx.io.Sink
import space.kscience.dataforge.meta.descriptors.MetaDescriptor
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

        assertEquals(1, iterated.size, "Native operations should find one envelope")
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

    @Test
    fun testSingleFileWithSubdirectories() {
        val operations = SingleFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(Meta { "key" put "value" }, null)
        val name = "sub.dir.test".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)

        assertTrue(tempDir.resolve("sub/dir/test.df").exists())

        val iterated = operations.iterate(tempDir).toList()
        assertEquals(1, iterated.size)
        assertEquals(name, iterated[0].first)
    }

    @Test
    fun testNativeFileWithSubdirectories() {
        val operations = NativeFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(
            Meta { "key" put "value" },
            "Hello".toByteArray().asBinary()
        )
        val name = "sub.dir.test".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)

        assertTrue(tempDir.resolve("sub/dir/test.df.json").exists())
        assertTrue(tempDir.resolve("sub/dir/test").exists())

        val iterated = operations.iterate(tempDir).toList()
        assertEquals(1, iterated.size)
        assertEquals(name, iterated[0].first)
    }

    @Test
    fun testMixedSubdirectories() {
        val singleOps = SingleFileEnvelopeOperations(ioPlugin)
        val nativeOps = NativeFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(Meta { "key" put "value" }, null)

        singleOps.writeEnvelope("a.b.c1".parseAsName(), tempDir, envelope)
        nativeOps.writeEnvelope("a.b.c2".parseAsName(), tempDir, envelope)
        singleOps.writeEnvelope("a.d.e".parseAsName(), tempDir, envelope)

        val iterated = nativeOps.iterate(tempDir).toList()
        assertEquals(3, iterated.size)
        assertTrue(iterated.any { it.first == "a.b.c1".parseAsName() })
        assertTrue(iterated.any { it.first == "a.b.c2".parseAsName() })
        assertTrue(iterated.any { it.first == "a.d.e".parseAsName() })
    }

    /*
     * LLM generated code: Added tests for FileEnvelopeOperations.read function on a single file.
     */
    @Test
    fun testSingleFileRead() {
        val operations = SingleFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(Meta { "key" put "value" }, null)
        val name = "test".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)

        val file = tempDir.resolve("test.df")
        val read = operations.read(file)
        assertEquals(1, read.size)
        assertTrue(read.containsKey(Name.EMPTY))
    }

    @Test
    fun testNativeReadSingleFileDirect() {
        val operations = NativeFileEnvelopeOperations(ioPlugin)
        val envelope = Envelope(
            Meta { "key" put "value" },
            "Hello".toByteArray().asBinary()
        )
        val name = "test".parseAsName()
        operations.writeEnvelope(name, tempDir, envelope)

        val metaFile = tempDir.resolve("test.df.json")
        val read = operations.read(metaFile)
        assertEquals(1, read.size, "Should be able to read single meta file as envelope")
        assertTrue(read.containsKey(Name.EMPTY))
        assertEquals("Hello", read[Name.EMPTY]?.data?.toByteArray()?.decodeToString())
    }

    @Test
    fun testNativeMetadataAppearsOnlyWhenComplete() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slowFormat = object : MetaFormatFactory by JsonMetaFormat {
            override fun writeMeta(sink: Sink, meta: Meta, descriptor: MetaDescriptor?) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                JsonMetaFormat.writeMeta(sink, meta, descriptor)
            }
        }
        val operations = NativeFileEnvelopeOperations(ioPlugin, slowFormat)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val writer = executor.submit {
                operations.writeEnvelope("record", tempDir, Envelope(Meta { "key" put "value" }, "body".toByteArray().asBinary()))
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertFalse(tempDir.resolve("record${operations.metaExtension}").exists())
            assertTrue(operations.envelopeFilesSequence(tempDir).none())
            release.countDown()
            writer.get(10, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdown()
        }
        val envelope = operations.readEnvelope(tempDir.resolve("record${operations.metaExtension}"))
        assertEquals("value", envelope?.meta?.get("key")?.string)
        assertEquals(listOf("record", "record${operations.metaExtension}"), tempDir.listDirectoryEntries().map { it.name }.sorted())
    }

    @Test
    fun testNativeMetadataDoesNotOverwriteAnotherEnvelope() {
        val operations = NativeFileEnvelopeOperations(ioPlugin)
        // a body name that matches a temporary metadata name of another envelope
        val bodyName = "record${operations.metaExtension}.tmp"
        operations.writeEnvelope(bodyName, tempDir, Envelope(Meta.EMPTY, "keep".toByteArray().asBinary()))
        operations.writeEnvelope("record", tempDir, Envelope(Meta { "key" put "value" }, null))
        assertEquals("keep", tempDir.resolve(bodyName).readText())
        assertEquals("value", operations.readEnvelope(tempDir.resolve("record${operations.metaExtension}"))?.meta?.get("key")?.string)
    }

    @Test
    fun testNativeEnvelopeWithLongNameIsWritable() {
        val operations = NativeFileEnvelopeOperations(ioPlugin)
        // the metadata name still fits the usual file name limit
        val name = "r".repeat(220)
        operations.writeEnvelope(name, tempDir, Envelope(Meta { "key" put "value" }, "body".toByteArray().asBinary()))
        val envelope = operations.readEnvelope(tempDir.resolve("$name${operations.metaExtension}"))
        assertEquals("value", envelope?.meta?.get("key")?.string)
    }

    @Test
    fun testNativeMetadataIsNotReplaced() {
        val operations = NativeFileEnvelopeOperations(ioPlugin)
        operations.writeEnvelope("record", tempDir, Envelope(Meta { "key" put "first" }, null))
        assertFailsWith<FileAlreadyExistsException> {
            operations.writeEnvelope("record", tempDir, Envelope(Meta { "key" put "second" }, null))
        }
        val envelope = operations.readEnvelope(tempDir.resolve("record${operations.metaExtension}"))
        assertEquals("first", envelope?.meta?.get("key")?.string)
        assertEquals(listOf("record${operations.metaExtension}"), tempDir.listDirectoryEntries().map { it.name })
    }
}
