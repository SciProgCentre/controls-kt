package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.dataplatform.TagTable.Companion.timeColumnHeader
import space.kscience.controls.instant
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.dataType
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.tables.Row
import space.kscience.tables.Rows
import space.kscience.tables.TableHeader
import space.kscience.tables.get
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchEvent
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Launch a directory monitor that calls [onEvent] for each file creation or deletion event.
 */
internal fun CoroutineScope.launchDirectoryMonitor(
    directory: Path,
    onEvent: suspend (kind: WatchEvent.Kind<*>, file: Path) -> Unit
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

/**
 * A class that represents an index for a data platform's storage, providing capabilities for
 * managing, querying, and maintaining intervals of stored data. The class organizes data by
 * intervals and supports both querying and real-time updates to the index.
 *
 * @property storage The IOPlugin used for file system interactions such as reading or monitoring files.
 * @property dataDirectory The directory where data files are stored and monitored.
 * @property cacheMetadata A flag indicating whether metadata should be cached for storage efficiency.
 * @property operations An instance of FileEnvelopeOperations to handle reading and writing envelope data.
 * @property rowsConverter A converter responsible for transforming envelopes into rows with metadata.
 * @property scope A CoroutineScope used for managing asynchronous operations.
 * @property removeFilesCycleDuration The duration interval for periodically checking and handling removed files.
 *
 * Implements:
 * - [ContextAware] to provide context for the operations performed in the index.
 * - [AutoCloseable] to ensure resources can be managed and released appropriately.
 *
 * Key Features:
 * 1. **Interval-Based Storage**:
 *    - Maintains data in intervals with start and end times and organizes them efficiently for search and retrieval.
 *    - Uses an AVL tree structure for balanced interval organization, supporting fast insert, remove, and search operations.
 *
 * 2. **Querying Capabilities**:
 *    - `selectEnvelopes`: Queries and retrieves envelope data that intersects with a specified time range.
 *    - `selectRows`: Queries and organizes rows within a specific time range, ensuring continuity across intervals.
 *
 * 3. **Data Insertion and Removal**:
 *    - Supports adding new data intervals and dynamically updating the index tree.
 *    - Handles safe removal of intervals while maintaining balanced tree operations.
 *
 * 4. **Asynchronous Monitoring**:
 *    - Monitors the `dataDirectory` for real-time changes, supporting automatic indexing of new files and tracking deletions.
 *
 * 5. **Synchronization and Cleanup**:
 *    - Employs a coroutine-based lifecycle, ensuring safety and concurrency for operations.
 *    - Periodically scans for and removes outdated or deleted files from the index.
 */
public class DataStorageIndex(
    public val storage: ControlsStoragePlugin,
    private val dataDirectory: Path,
    private val cacheMetadata: Boolean = true,
    private val operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(storage.io),
    private val scope: CoroutineScope = storage.context,
    private val removeFilesCycleDuration: Duration = 10.minutes
) : ContextAware, AutoCloseable {

    override val context: Context get() = storage.context

    private data class Interval(var start: Instant, var end: Instant, val path: Path)

    private class IntervalNode(
        var interval: Interval,
        var left: IntervalNode? = null,
        var right: IntervalNode? = null,
        var height: Int = 1,
        var maxEnd: Instant = interval.end
    )


    private var root: IntervalNode? = null


    /**
     * Select row envelopes in [range]
     */
    public fun selectEnvelopes(range: ClosedRange<Instant>): List<Envelope> =
        search(range).sortedBy { it.start }.mapNotNull { operations.readEnvelope(it.path) }

    /**
     * Select all rows in a given range
     */
    public fun selectRows(range: ClosedRange<Instant>): Rows<Meta> {
        val parts = selectEnvelopes(range).sortedBy {
            it.meta[RowEnvelopeMetaSpec.startTime]
        }.map {
            val envelopeType = it.dataType ?: ZipRowsEnvelopeConverter.ENVELOPE_TYPE
            val converter = storage.rowEnvelopeConverters[envelopeType] ?:error("Can't find rows converter for envelope type $envelopeType")
            converter.readRows(it)
        }

        return object : Rows<Meta> {
            override val headers: TableHeader<Meta> = buildList {
                parts.forEach { addAll(it.headers) }
            }.distinct()

            override fun rowSequence(): Sequence<Row<Meta>> = parts.asSequence().flatMap {
                it.rowSequence()
            }.filter { (it[timeColumnHeader].instant ?: Instant.DISTANT_PAST) in range }

        }
    }

    // -------------------------
    // TOP LEVEL API
    // -------------------------
    private fun insert(name: Name, path: Path): Interval? {
        val envelope = operations.readEnvelope(path) ?: return null
        val startTime = envelope.meta[RowEnvelopeMetaSpec.startTime]
        val endTime = envelope.meta[RowEnvelopeMetaSpec.endTime]

        if (startTime == null || endTime == null) {
            logger.warn { "Start or end time is not defined for envelope $name" }
            return null
        }

        val interval = Interval(
            start = startTime,
            end = endTime,
            path = path,
        )

        root = insert(root, interval)

        return interval
    }


    private fun remove(interval: Interval) {
        root = remove(root, interval)
    }

    private fun search(query: ClosedRange<Instant>): List<Interval> {
        val result = mutableListOf<Interval>()
        search(root, query, result)
        return result
    }

    // -------------------------
    // INSERT
    // -------------------------
    private fun insert(node: IntervalNode?, interval: Interval): IntervalNode {
        node ?: return IntervalNode(interval)

        when {
            interval.start < node.interval.start ->
                node.left = insert(node.left, interval)

            else ->
                node.right = insert(node.right, interval)
        }

        update(node)
        return balance(node)
    }

    // -------------------------
    // REMOVE
    // -------------------------
    private fun remove(node: IntervalNode?, interval: Interval): IntervalNode? {
        node ?: return null

        when {
            interval.start < node.interval.start ->
                node.left = remove(node.left, interval)

            interval.start > node.interval.start ->
                node.right = remove(node.right, interval)

            else -> {
                if (node.interval.end != interval.end) {
                    // Same start, different end → go right
                    node.right = remove(node.right, interval)
                } else {
                    // Node found
                    if (node.left == null || node.right == null) {
                        return node.left ?: node.right
                    }

                    // Replace with inorder successor
                    val successor = minNode(node.right!!)
                    node.interval = successor.interval
                    node.right = remove(node.right, successor.interval)
                }
            }
        }

        update(node)
        return balance(node)
    }

    // -------------------------
    // SEARCH INTERSECTING
    // -------------------------
    private fun search(node: IntervalNode?, query: ClosedRange<Instant>, result: MutableList<Interval>) {
        node ?: return

        if (node.interval.start <= query.endInclusive && node.interval.end >= query.start)
            result.add(node.interval)

        if (node.left != null && node.left!!.maxEnd >= query.start)
            search(node.left, query, result)

        search(node.right, query, result)
    }

    private fun search(
        node: IntervalNode?,
        predicate: (Interval) -> Boolean,
        out: MutableList<Interval>
    ) {
        node ?: return

        if (predicate(node.interval)) {
            out.add(node.interval)
        }

        search(node.left, predicate, out)
        search(node.right, predicate, out)
    }

    // -------------------------
    // AVL HELPERS
    // -------------------------
    private fun height(n: IntervalNode?) = n?.height ?: 0

    private fun update(n: IntervalNode) {
        n.height = 1 + maxOf(height(n.left), height(n.right))
        n.maxEnd = maxOf(
            n.interval.end,
            n.left?.maxEnd ?: Instant.DISTANT_FUTURE,
            n.right?.maxEnd ?: Instant.DISTANT_PAST
        )
    }

    private fun balanceFactor(n: IntervalNode) =
        height(n.left) - height(n.right)

    private fun balance(n: IntervalNode): IntervalNode {
        val bf = balanceFactor(n)

        return when {
            bf > 1 && balanceFactor(n.left!!) >= 0 -> rotateRight(n)
            bf > 1 -> {
                n.left = rotateLeft(n.left!!)
                rotateRight(n)
            }

            bf < -1 && balanceFactor(n.right!!) <= 0 -> rotateLeft(n)
            bf < -1 -> {
                n.right = rotateRight(n.right!!)
                rotateLeft(n)
            }

            else -> n
        }
    }

    private fun rotateLeft(z: IntervalNode): IntervalNode {
        val y = z.right!!
        val t2 = y.left

        y.left = z
        z.right = t2

        update(z)
        update(y)

        return y
    }

    private fun rotateRight(z: IntervalNode): IntervalNode {
        val y = z.left!!
        val t3 = y.right

        y.right = z
        z.left = t3

        update(z)
        update(y)

        return y
    }

    private fun minNode(n: IntervalNode): IntervalNode {
        var cur = n
        while (cur.left != null) cur = cur.left!!
        return cur
    }

    private fun removeIf(predicate: (Interval) -> Boolean) {
        val toRemove = mutableListOf<Interval>()
        search(root, predicate, toRemove)
        for (interval in toRemove) {
            remove(interval)
        }
    }

    private var monitorJob: Job? = null

    /**
     * Start indexer and wait for initial indexing to be complete
     */
    public fun open(): Unit {

        operations.envelopeFilesSequence(dataDirectory).forEach { (name, path) ->
            insert(name, path)
        }

        monitorJob = scope.launch(Dispatchers.IO) {

            val removedFiles = mutableListOf<Path>()

            val removalMutex: Mutex = Mutex()

            launchDirectoryMonitor(dataDirectory) { kind, file ->

                when (kind) {
                    ENTRY_CREATE -> {
                        val tokens = file.relativeTo(dataDirectory).map { NameToken.parse(it.name) }
                        insert(Name(tokens), file)
                    }

                    ENTRY_DELETE -> removalMutex.withLock {
                        removedFiles.add(file)
                    }
                }
            }

            launch {
                while (isActive) {
                    delay(removeFilesCycleDuration)
                    removalMutex.withLock {
                        removeIf {
                            it.path in removedFiles
                        }
                        removedFiles.clear()
                    }
                }
            }
        }
    }

    override fun close() {
        monitorJob?.cancel()
    }

}