package storage

import kotlinx.coroutines.*
import space.kscience.controls.instant
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchEvent
import java.util.*
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.time.Instant

/**
 * Launch a directory monitor that calls [onEvent] for each file creation or deletion event.
 */
internal fun CoroutineScope.launchDirectoryMonitor(
    directory: Path,
    onEvent: (kind: WatchEvent.Kind<*>, file: Path) -> Unit
): Job = launch(Dispatchers.IO) {
    FileSystems.getDefault().newWatchService().use { watchService ->
        directory.register(
            watchService,
            ENTRY_CREATE, ENTRY_DELETE,
        )

        while (isActive) {
            val key = try {
                watchService.take()   // blocking, interruptible by close()
            } catch (ex: ClosedWatchServiceException) {
                break
            }

            for (event in key.pollEvents()) {
                ensureActive()
                val file = event.context() as Path
                onEvent(event.kind(), file)
            }

            if (!key.reset()) break
        }
    }
}


public class DataPlatformStorageIndex(
    public val io: IOPlugin,
    public val dataDirectory: Path,
    public val cacheMetadata: Boolean = true,
    private val operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(io),
    private val scope: CoroutineScope = io.context,
) : ContextAware {

    override val context: Context get() = io.context

    private class IndexEntry(
        val name: Name,
        val path: Path,
        val startTime: Instant,
        val endTime: Instant? = null,
        val meta: Meta? = null,
    ) : Comparable<IndexEntry> {
        override fun compareTo(other: IndexEntry): Int = startTime.compareTo(other.startTime)
    }

    private val index = TreeSet<IndexEntry>()

    //TODO add pre-indexed data

    private val indexJob = scope.launch(Dispatchers.IO) {

        fun indexFile(name: Name, path: Path): IndexEntry? {
            val envelope = operations.readEnvelope(path) ?: return null
            val time = envelope.meta["startTime"]?.instant ?: envelope.meta["@envelope.time"]?.instant
            if (time == null) {
                logger.warn { "Time is not defined for envelope $name" }
                return null
            }

            val endTime = envelope.meta["endTime"]?.instant

            val indexEntry = IndexEntry(
                name = name,
                path = path,
                startTime = time,
                endTime = endTime,
                meta = if (cacheMetadata) envelope.meta else null,
            )

            index.add(indexEntry)

            return indexEntry
        }

        operations.envelopeFilesSequence(dataDirectory).forEach { (name, path) ->
            indexFile(name, path)
        }

        launchDirectoryMonitor(dataDirectory) { kind, file ->

            when (kind) {
                ENTRY_CREATE -> {
                    val tokens = file.relativeTo(dataDirectory).map { NameToken.parse(it.name) }
                    indexFile(Name(tokens), file)
                }

                ENTRY_DELETE -> {
                    index.removeAll {
                        it.path == file
                    }
                }
            }
        }

        suspend fun selectEnvelopes(range: ClosedRange<Instant>): Sequence<IndexEntry> = sequence {

        }
    }


}