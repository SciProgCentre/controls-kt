package space.kscience.controls.storage

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readLine
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import space.kscience.controls.api.DeviceErrorMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.duration
import space.kscience.controls.nullable
import space.kscience.controls.time.clock
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.io.*
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.io.path.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Implementation of the [DeviceMessageStorage] interface that stores device messages
 * in files within a specified directory. Incoming messages are grouped and stored in
 * JSONL (JSON Lines) files based on their type, with file rotation and indexing mechanisms
 * for efficient read and retrieval operations.
 *
 * @property directory The directory where JSONL files are stored and managed.
 * @property maxMessagesPerFile The maximum number of messages allowed in a single file
 * before creating a new file. Defaults to 10,000 messages.
 * @property historyDepth The retention period for messages. Files older than this duration
 * are not indexed or included in results during read operations. Files themselves are not deleted, just not indexed.
 * If null, no retention limit is applied.
 * @property clock A clock instance used for timestamping file creation and indexing operations.
 * Defaults to the system clock.
 * @property compressHistory If true, rotated JSONL files are converted in the background to DataForge envelopes containing ZIP-compressed JSONL lines.
 */
public class FileDeviceMessageStorage(
    public val directory: Path,
    private val maxMessagesPerFile: Int = 10000,
    private val historyDepth: Duration? = null,
    public val compressHistory: Boolean = false,
    private val clock: Clock = Clock.System
) : WriteableDeviceMessageStorage {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // writing

    private val fileUpdateMutex = Mutex()

    private val currentFiles = mutableMapOf<String, Path>()

    private val messageCounters = ConcurrentHashMap<String, Int>()

    private suspend fun getOrCreateCurrentFileForEvent(
        messageType: String
    ): Path = fileUpdateMutex.withLock {
        currentFiles.getOrPut(messageType) {
            val suffix = clock.now().toString().replace(":", "-")
            var fileName = "${messageType}-$suffix.jsonl"
            var candidate = directory.resolve(fileName)
            var counter = 0
            while (candidate.exists() || candidate.resolveSibling("${candidate.nameWithoutExtension}.df").exists()) {
                counter++
                fileName = "${messageType}-$suffix-$counter.jsonl"
                candidate = directory.resolve(fileName)
            }
            logger.info("Creating a storage file for message type $messageType: $fileName")
            candidate.also {
                it.parent.createDirectories()
            }
        }
    }

    /*
     * LLM generated code: Compress rotated JSONL file into a DataForge envelope with ZIP/DEFLATE compressed lines in the background.
     */
    private suspend fun compressFile(file: Path) = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val dfFile = file.resolveSibling("${file.nameWithoutExtension}.df")
        val tmpFile = file.resolveSibling("${file.nameWithoutExtension}.df.tmp")
        try {
            logger.info("Compressing rotated file {} to {}", file.fileName, dfFile.fileName)
            val baos = ByteArrayOutputStream()
            DeflaterOutputStream(baos).use { deflater ->
                file.inputStream().use { input ->
                    input.copyTo(deflater)
                }
            }
            val binary = baos.toByteArray().asBinary()
            val meta = Meta {
                Envelope.ENVELOPE_DESCRIPTION_KEY put "Device messages JSONL compressed with ZIP/DEFLATE"
                Envelope.ENVELOPE_DATA_TYPE_KEY put "jsonl.zip"
            }
            val envelope = Envelope(meta, binary)
            tmpFile.write {
                TaggedEnvelopeFormat.writeTo(this, envelope)
            }
            val originalLastModified = Files.getLastModifiedTime(file)
            Files.move(
                tmpFile,
                dfFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            try {
                Files.setLastModifiedTime(dfFile, originalLastModified)
            } catch (e: Exception) {
                logger.warn("Failed to set last modified time for ${dfFile.fileName}", e)
            }

            val dfIndex = FileIndex(dfFile)
            dfIndex.getEntries()
            indexCache.put(dfFile, dfIndex)
            indexCache.invalidate(file)

            file.deleteIfExists()
            logger.info("Successfully compressed and deleted original file {}", file.fileName)
        } catch (e: Exception) {
            logger.error("Failed to compress file ${file.fileName}", e)
            tmpFile.deleteIfExists()
        }
    }

    override suspend fun write(event: DeviceMessage) {
        //TODO add buffering

        val messageType = DeviceMessage.serialNameFor(event)
        val file = getOrCreateCurrentFileForEvent(messageType)
        logger.trace("Writing event {} to file {}", event, file)
        file.append {
            writeString(Json.encodeToString(event))
            writeString("\n")
        }
        val count = messageCounters.getOrDefault(messageType, 0) + 1
        messageCounters[messageType] = count
        if (count >= maxMessagesPerFile) {
            logger.info("Rotating file for message type {}", messageType)
            val rotatedFile = fileUpdateMutex.withLock {
                //remove file from cache to rotate it on next write
                messageCounters[messageType] = 0
                currentFiles.remove(messageType)
            }
            if (compressHistory && rotatedFile != null) {
                scope.launch {
                    compressFile(rotatedFile)
                }
            }
        }
    }

    // indexing and reading

    private sealed interface IndexEntry {
        val time: Instant
        fun readOrNull(): DeviceMessage?

        class File(
            override val time: Instant,
            val path: Path,
            val offset: Int
        ) : IndexEntry {
            override fun readOrNull(): DeviceMessage? = try {
                if (!path.exists()) {
                    val dfPath = path.resolveSibling("${path.nameWithoutExtension}.df")
                    if (dfPath.exists()) {
                        val envelope = dfPath.read { TaggedEnvelopeFormat.readFrom(this) }
                        val data = envelope.data?.toByteArray()
                        if (data != null && data.isNotEmpty()) {
                            val decompressed = InflaterInputStream(ByteArrayInputStream(data)).readBytes()
                            val msg = decompressed.inputStream().bufferedReader().useLines { lines ->
                                lines.filter { it.isNotBlank() }.map {
                                    Json.decodeFromString<DeviceMessage>(it)
                                }.find { it.time == time }
                            }
                            if (msg != null) return msg
                        }
                    }
                }
                path.read {
                    skip(offset.toLong())
                    val string = readLine() ?: return@read null
                    Json.decodeFromString<DeviceMessage>(string)
                }
            } catch (e: Exception) {
                logger.error("Failed to read message from file ${path.fileName}", e)
                DeviceErrorMessage(
                    time = Clock.System.now(),
                    errorMessage = "Failed to read message from file ${path.fileName}: ${e.message}",
                    errorStackTrace = e.stackTraceToString()
                )
            }
        }

        class Message(
            override val time: Instant,
            val message: DeviceMessage
        ) : IndexEntry {
            override fun readOrNull(): DeviceMessage = message
        }
    }

    /**
     * A message index for a single file. Each file stores messages of a single event type
     */
    private class FileIndex(
        val path: Path
    ) {

        private var indexerPosition = 0

        private val mutex = Mutex()

        private val _entries = mutableListOf<IndexEntry>()

        suspend fun getEntries(): List<IndexEntry> = mutex.withLock {
            if (!path.exists()) return@withLock _entries

            if (path.extension == "df") {
                if (_entries.isEmpty()) {
                    try {
                        val envelope = path.read { TaggedEnvelopeFormat.readFrom(this) }
                        val data = envelope.data?.toByteArray()
                        if (data != null && data.isNotEmpty()) {
                            val decompressed = InflaterInputStream(ByteArrayInputStream(data)).readBytes()
                            decompressed.inputStream().bufferedReader().useLines { lines ->
                                for (line in lines) {
                                    if (line.isBlank()) continue
                                    try {
                                        val message = Json.decodeFromString<DeviceMessage>(line)
                                        _entries.add(
                                            IndexEntry.Message(
                                                time = message.time,
                                                message = message
                                            )
                                        )
                                    } catch (e: Exception) {
                                        logger.error(
                                            "Failed to parse message from envelope file ${path.fileName}",
                                            e
                                        )
                                        _entries.add(
                                            IndexEntry.Message(
                                                time = Clock.System.now(),
                                                message = DeviceErrorMessage(
                                                    time = Clock.System.now(),
                                                    errorMessage = "Failed to parse message from envelope file ${path.fileName}: ${e.message}",
                                                    errorStackTrace = e.stackTraceToString()
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to read envelope file ${path.fileName}", e)
                    }
                }
            } else {
                // automatically index on each flow read
                if (indexerPosition < path.fileSize()) {
                    path.read {
                        skip(indexerPosition.toLong())
                        while (true) {
                            val line = readLine() ?: break
                            try {
                                val message = Json.decodeFromString<DeviceMessage>(line)
                                _entries.add(
                                    IndexEntry.File(
                                        time = message.time,
                                        path = path,
                                        offset = indexerPosition
                                    )
                                )
                            } catch (e: Exception) {
                                logger.error(
                                    "Failed to index message in file ${path.fileName} at offset $indexerPosition",
                                    e
                                )
                                _entries.add(
                                    IndexEntry.File(
                                        time = Clock.System.now(),
                                        path = path,
                                        offset = indexerPosition
                                    )
                                )
                            }
                            indexerPosition += line.encodeToByteArray().size + 1
                        }
                    }
                }
            }
            _entries
        }


        fun flow(range: ClosedRange<Instant>?): Flow<IndexEntry> = flow {
            if (range == null) {
                getEntries().forEach {
                    emit(it)
                }
            } else {
                getEntries().filter { it.time in range }.forEach {
                    emit(it)
                }
            }
        }
    }

    private val indexCache = Cache.Builder<Path, FileIndex>()
        .expireAfterWrite(historyDepth ?: 2.hours)
        .build()

    private suspend fun getIndexedFiles(): Map<String, Collection<FileIndex>> {
        val now = clock.now()
        if (!directory.exists()) return emptyMap()
        val dfFiles = directory.listDirectoryEntries("*.df")
        val jsonlFiles = directory.listDirectoryEntries("*.jsonl")
        val allFiles = (dfFiles + jsonlFiles).filter {
            historyDepth == null || (now - it.getLastModifiedTime().toInstant().toKotlinInstant()) < historyDepth
        }.distinctBy { it.nameWithoutExtension }

        return allFiles.groupBy { it.nameWithoutExtension.substringBefore("-") }.mapValues { entry ->
            entry.value.map { path ->
                indexCache.get(path) {
                    logger.debug("Loading index for file {}", path)
                    FileIndex(path)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun flowDirectoryIndices(
        eventType: String?,
        range: ClosedRange<Instant>?,
    ): Flow<IndexEntry> = flow {
        val indexedFiles = getIndexedFiles()
        if (eventType == null) {
            indexedFiles.values.flatten().asFlow().flatMapMerge {
                it.flow(range)
            }.collect {
                emit(it)
            }
        } else {
            indexedFiles[eventType]?.asFlow()?.flatMapMerge {
                it.flow(range)
            }?.collect {
                emit(it)
            }
        }

    }

    override fun read(
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?
    ): Flow<DeviceMessage> = flow {
        flowDirectoryIndices(null, range).collect {
            val event = it.readOrNull()
            if (event != null && (sourceDevice == null || sourceDevice == event.sourceDevice) && (targetDevice == null || targetDevice == event.targetDevice)) {
                emit(event)
            }
        }
    }

    override fun read(
        eventType: String,
        range: ClosedRange<Instant>?,
        sourceDevice: Name?,
        targetDevice: Name?
    ): Flow<DeviceMessage> = flow {
        flowDirectoryIndices(eventType, range).collect {
            val event = it.readOrNull()
            if (event != null && (sourceDevice == null || sourceDevice == event.sourceDevice) && (targetDevice == null || targetDevice == event.targetDevice)) {
                emit(event)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    public companion object : MetaSpec(), Factory<DeviceMessageStorage> {
        private val logger = LoggerFactory.getLogger(FileDeviceMessageStorage::class.java)

        public val directory: MetaRef<String> by string{
            description = "Absolute or relative path to the directory where the messages are stored"
        }

        private val maxMessagesPerFile: MetaRef<Int> by int{
            default(1000)
        }
        private val historyDepth: MetaRef<Duration?> by item(MetaConverter.duration.nullable())

        public val compressHistory: MetaRef<Boolean> by boolean {
            default(false)
        }

        override fun build(
            context: Context,
            meta: Meta
        ): DeviceMessageStorage  = FileDeviceMessageStorage(
            directory = Path(meta[directory] ?: error("Directory is not defined")),
            maxMessagesPerFile = meta[maxMessagesPerFile] ?: 1000,
            historyDepth = meta[historyDepth],
            compressHistory = meta[compressHistory] ?: false,
            clock = context.clock
        )
    }
}