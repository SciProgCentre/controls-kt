package space.kscience.controls.storage

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import space.kscience.dataforge.io.append
import space.kscience.dataforge.io.read
import space.kscience.dataforge.names.Name
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
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
 */
public class FileDeviceMessageStorage(
    public val directory: Path,
    private val maxMessagesPerFile: Int = 10000,
    private val historyDepth: Duration? = null,
    private val clock: Clock = Clock.System
) : DeviceMessageStorage {

    // writing

    private val fileUpdateMutex = Mutex()

    private val currentFiles = mutableMapOf<String, Path>()

    private val messageCounters = ConcurrentHashMap<String, Int>()

    private suspend fun getOrCreateCurrentFileForEvent(
        messageType: String
    ): Path = currentFiles.getOrPut(messageType) {
        fileUpdateMutex.withLock {
            val suffix = clock.now().toString().replace(":", "-")
            val fileName = "${messageType}-$suffix.jsonl"
            logger.info("Creating a storage file for message type $messageType: $fileName")
            directory.resolve(fileName).also {
                it.parent.createDirectories()
            }
        }
    }

    override suspend fun write(event: DeviceMessage) {
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
            fileUpdateMutex.withLock {
                //remove file from cache to rotate it on next write
                currentFiles.remove(messageType)
                messageCounters[messageType] = 0
            }
        }
    }

    // indexing and reading

    private class IndexEntry(val time: Instant, val path: Path, val offset: Int) {
        fun readOrNull(): DeviceMessage? = path.read {
            try {
                skip(offset.toLong())
                val string = readLine() ?: return@read null
                Json.decodeFromString<DeviceMessage>(string)
            } catch (e: Exception) {
                logger.error("Failed to read message from file ${path.fileName}", e)
                DeviceErrorMessage(
                    time = Clock.System.now(),
                    errorMessage = "Failed to read message from file ${path.fileName}: ${e.message}",
                    errorStackTrace = e.stackTraceToString()
                )
            }
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

        private suspend fun getEntries(): List<IndexEntry> = mutex.withLock {
            // automatically index on each flow read
            if (indexerPosition < path.fileSize()) {
                path.read {
                    skip(indexerPosition.toLong())
                    while (true) {
                        val line = readLine() ?: break
                        try {
                            val message = Json.decodeFromString<DeviceMessage>(line)
                            _entries.add(
                                IndexEntry(
                                    time = message.time,
                                    path = path,
                                    offset = indexerPosition
                                )
                            )
                        } catch (e: Exception) {
                            logger.error("Failed to index message in file ${path.fileName} at offset $indexerPosition", e)
                            _entries.add(
                                IndexEntry(
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
        return directory.listDirectoryEntries("*.jsonl").filter {
            historyDepth == null || (now - it.getLastModifiedTime().toInstant().toKotlinInstant()) < historyDepth
        }.groupBy { it.nameWithoutExtension.substringBefore("-") }.mapValues {
            it.value.map { path ->
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

    }

    public companion object {
        private val logger = LoggerFactory.getLogger(FileDeviceMessageStorage::class.java)
    }
}