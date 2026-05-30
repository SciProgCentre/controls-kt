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
     * Width first iteration over all envelopes in the given directory and subdirectories
     */
    public fun iterate(directory: Path): Sequence<Pair<Name, Envelope>>

    public companion object {
        public const val FILE_EXTENSION: String = "df"
    }
}

/**
 * Read all envelopes from the given directory with the given maximum depth
 */
public fun FileEnvelopeOperations.readDirectory(directory: Path, maxDepth: Int = 1): Map<Name, Envelope> =
    iterate(directory).takeWhile { it.first.length < maxDepth }.associate { it.first to it.second }

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

    override fun iterate(directory: Path): Sequence<Pair<Name, Envelope>> = sequence {
        val queue = ArrayDeque<Pair<Name, Path>>()
        queue.add(Name.EMPTY to directory)

        while (queue.isNotEmpty()) {
            val (currentName, currentFile) = queue.removeFirst()

            if (currentFile.extension == FILE_EXTENSION) {
                try {
                    val envelope = ioPlugin.readEnvelopeFile(currentFile)
                    yield(currentName to envelope)
                } catch (e: Exception) {
                    ioPlugin.logger.error(e) { "Failed to read envelope from $currentFile" }
                }
            } else if (Files.isDirectory(currentFile)) {
                Files.newDirectoryStream(currentFile).use {
                    it.forEach { child: Path ->
                        queue.add((currentName + child.nameWithoutExtension) to child)
                    }
                }
            }
        }
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

    override fun iterate(directory: Path): Sequence<Pair<Name, Envelope>> = sequence {
        val queue = ArrayDeque<Pair<Name, Path>>()
        queue.add(Name.EMPTY to directory)

        while (queue.isNotEmpty()) {
            val (currentName, currentFile) = queue.removeFirst()

            when {
                currentFile.extension == FILE_EXTENSION -> {
                    try {
                        val envelope = ioPlugin.readEnvelopeFile(currentFile, false)
                        yield(currentName to envelope)
                    } catch (e: Exception) {
                        ioPlugin.logger.error(e) { "Failed to read envelope from $currentFile" }
                    }
                }
                currentFile.isDirectory() -> {
                    Files.newDirectoryStream(currentFile).use { directoryStream ->
                        directoryStream.forEach { child: Path ->
                            if(child.fileName.toString().endsWith(metaExtension)){
                                try {
                                    val envelopeName = child.fileName.toString().removeSuffix(metaExtension)
                                    val meta = metaFormatFactory.readFrom(child.asBinary())
                                    val dataFile = child.resolveSibling(envelopeName)
                                    val binary = if (dataFile.exists()) dataFile.asBinary() else null
                                    yield((currentName + envelopeName) to Envelope(meta, binary))
                                } catch (e: Exception) {
                                    ioPlugin.logger.error(e) { "Failed to read envelope from $child" }
                                }
                            } else {
                                queue.add((currentName + child.nameWithoutExtension) to child)
                            }
                        }
                    }
                }
            }
        }
    }


}