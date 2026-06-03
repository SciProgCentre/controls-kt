package space.kscience.controls.storage

import space.kscience.controls.storage.FileEnvelopeOperations.Companion.FILE_EXTENSION
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.io.*
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.names.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

//TODO move to DataForge
//TODO process file overwrites

/**
 * A reader and writer for file-based envelope storage
 */
public interface FileEnvelopeOperations {

    /**
     * Write an envelope to the given directory
     */
    public fun writeEnvelope(fileName: String, directory: Path, envelope: Envelope)

    /**
     * Provide a sequence of files that could contain envelopes
     */
    public fun envelopeFilesSequence(root: Path): Sequence<Pair<Name, Path>>

    /**
     * Read a single envelope file if possible
     */
    public fun readEnvelope(path: Path): Envelope?

    /**
     * Width first iteration over all envelopes in the given directory and subdirectories
     */
    public fun iterate(directory: Path): Sequence<Pair<Name, Envelope>> = envelopeFilesSequence(directory)
        .mapNotNull { (name, path) -> readEnvelope(path)?.let { Pair(name, it) } }

    public companion object {
        public const val FILE_EXTENSION: String = "df"
    }
}

/**
 * Read a file or directory and return all envelopes found in it
 */
public fun FileEnvelopeOperations.read(path: Path, maxDepth: Int = Int.MAX_VALUE): Map<Name, Envelope> =
    iterate(path).takeWhile { it.first.length < maxDepth }.associate { it.first to it.second }

public fun FileEnvelopeOperations.writeEnvelope(name: Name, directory: Path, envelope: Envelope) {
    check(!name.isEmpty()) { "Envelope file name could not be empty" }
    if (name.length == 1) {
        writeEnvelope(name.toString(), directory, envelope)
    } else {
        writeEnvelope(
            fileName = name.last().toString(),
            directory = directory.resolve(name.tokens.dropLast(1).joinToString(separator = "/")),
            envelope = envelope
        )
    }
}

/**
 * Basic implementation of [FileEnvelopeOperations] using single file/directory reader and writer
 */
@OptIn(DFExperimental::class)
public class SingleFileEnvelopeOperations(
    public val ioPlugin: IOPlugin,
    public val envelopeFormat: EnvelopeFormat = TaggedEnvelopeFormat
) : FileEnvelopeOperations {

    override fun writeEnvelope(
        fileName: String,
        directory: Path,
        envelope: Envelope
    ) {
        directory.createDirectories()
        val fileName = directory.resolve("$fileName.$FILE_EXTENSION")

        ioPlugin.writeEnvelopeFile(fileName, envelope, envelopeFormat)
    }

    override fun envelopeFilesSequence(root: Path): Sequence<Pair<Name, Path>> = sequence {
        val queue = ArrayDeque<Pair<Name, Path>>()
        queue.add(Name.EMPTY to root)

        while (queue.isNotEmpty()) {
            val (currentName, currentFile) = queue.removeFirst()

            if (currentFile.extension == FILE_EXTENSION) {
                yield(currentName to currentFile)
            } else if (Files.isDirectory(currentFile)) {
                Files.newDirectoryStream(currentFile).use {
                    it.forEach { child: Path ->
                        queue.add((currentName + child.nameWithoutExtension) to child)
                    }
                }
            }
        }
    }


    override fun readEnvelope(path: Path): Envelope? = try {
        ioPlugin.readEnvelopeFile(path)
    } catch (e: Exception) {
        ioPlugin.logger.error(e) { "Failed to read envelope from $path" }
        null
    }

}

/**
 * A [FileEnvelopeOperations] that allows to store binary files with their native extensions
 */
public class NativeFileEnvelopeOperations(
    public val ioPlugin: IOPlugin,
    public val metaFormatFactory: MetaFormatFactory = JsonMetaFormat
) : FileEnvelopeOperations {

    public val metaExtension: String = ".$FILE_EXTENSION.${metaFormatFactory.shortName}"

    override fun writeEnvelope(
        fileName: String,
        directory: Path,
        envelope: Envelope
    ) {
        directory.createDirectories()
        envelope.data?.let { data ->
            directory.resolve(fileName).write {
                writeBinary(data)
            }
        }

        val metaFile = directory.resolve("$fileName$metaExtension")
        metaFile.write {
            metaFormatFactory.writeMeta(this, envelope.meta)
        }
    }

    override fun envelopeFilesSequence(root: Path): Sequence<Pair<Name, Path>> = sequence {
        val queue = ArrayDeque<Pair<Name, Path>>()
        queue.add(Name.EMPTY to root)

        while (queue.isNotEmpty()) {
            val (currentName, currentFile) = queue.removeFirst()

            when {
                currentFile.extension == FILE_EXTENSION -> {
                    yield(currentName to currentFile)
                }

                currentFile.fileName.toString().endsWith(metaExtension) -> {
                    yield(currentName to currentFile)
                }

                currentFile.isDirectory() -> {
                    Files.newDirectoryStream(currentFile).use { directoryStream ->
                        directoryStream.forEach { child: Path ->
                            val childName = child.fileName.toString().removeSuffix(metaExtension).removeSuffix(".$FILE_EXTENSION")
                            yield((currentName + childName) to child)
                        }
                    }
                }
            }
        }
    }

    override fun readEnvelope(path: Path): Envelope? = try {
        if (path.extension == FILE_EXTENSION) {
            ioPlugin.readEnvelopeFile(path)
        } else if (path.fileName.toString().endsWith(metaExtension)) {
            val envelopeName = path.fileName.toString().removeSuffix(metaExtension)
            val meta = metaFormatFactory.readFrom(path.asBinary())
            val dataFile = path.resolveSibling(envelopeName)
            val binary = if (dataFile.exists()) dataFile.asBinary() else null
            Envelope(meta, binary)
        } else {
            ioPlugin.logger.error { "Envelope file does not have proper envelope extension: $path" }
            null
        }
    } catch (e: Exception) {
        ioPlugin.logger.error(e) { "Failed to read envelope from $path" }
        null
    }

}