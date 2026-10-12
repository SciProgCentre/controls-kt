package space.kscience.controls.tagtable.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.LifecycleState
import space.kscience.controls.api.WithLifeCycle
import space.kscience.controls.instant
import space.kscience.controls.storage.ControlsStoragePlugin
import space.kscience.controls.storage.FileEnvelopeOperations
import space.kscience.controls.storage.NativeFileEnvelopeOperations
import space.kscience.controls.storage.ZipRowsEnvelopeConverter
import space.kscience.controls.tagtable.TagTable.Companion.timeColumnHeader
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.dataforge.io.Envelope
import space.kscience.dataforge.io.dataType
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.tables.Row
import space.kscience.tables.Rows
import space.kscience.tables.TableHeader
import space.kscience.tables.get
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Create a watcher for file creation and deletion events in this directory.
 */
internal fun Path.watchFiles(): WatchService = fileSystem.newWatchService().also { watchService ->
    try {
        register(watchService, ENTRY_CREATE, ENTRY_DELETE)
    } catch (ex: Throwable) {
        watchService.close()
        throw ex
    }
}

/**
 * Launch a directory monitor that calls [onEvent] with a path relative to [directory]
 * for each file creation or deletion event, and without a path for [OVERFLOW], when events were lost.
 * Cancellation interrupts a pending wait, so the watcher is closed instead of being left open.
 */
internal fun CoroutineScope.launchDirectoryMonitor(
    directory: Path,
    onEvent: suspend (kind: WatchEvent.Kind<*>, file: Path?) -> Unit
): Job = launchDirectoryMonitor(directory.watchFiles(), onEvent)

/**
 * Launch a directory monitor for an already registered [watchService] and close it when the monitor stops,
 * including a monitor that is cancelled before it starts.
 */
internal fun CoroutineScope.launchDirectoryMonitor(
    watchService: WatchService,
    onEvent: suspend (kind: WatchEvent.Kind<*>, file: Path?) -> Unit
): Job = launch(Dispatchers.IO) {
    watchService.use { watchService ->
        while (isActive) {
            val key = try {
                runInterruptible { watchService.take() }
            } catch (ex: ClosedWatchServiceException) {
                break
            }

            for (event in key.pollEvents()) {
                ensureActive()
                val file = event.context() as Path?
                onEvent(event.kind(), file)
            }

            if (!key.reset()) break
        }
    }
}.apply { invokeOnCompletion { watchService.close() } }

/**
 * A class that represents an index for a data platform's storage, providing capabilities for
 * managing, querying, and maintaining intervals of stored data. The class organizes data by
 * intervals and supports both querying and real-time updates to the index.
 *
 * @property storage The IOPlugin used for file system interactions such as reading or monitoring files.
 * @property dataDirectory The directory where data files are stored and monitored.
 * @property cacheMetadata A flag indicating whether metadata should be cached for storage efficiency.
 * @property operations An instance of FileEnvelopeOperations to handle reading and writing envelope data.
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
public class TableStorageIndex(
    public val storage: ControlsStoragePlugin,
    private val dataDirectory: Path,
    private val cacheMetadata: Boolean = true,
    private val operations: FileEnvelopeOperations = NativeFileEnvelopeOperations(storage.io),
    private val scope: CoroutineScope = storage.context,
    private val removeFilesCycleDuration: Duration = 10.minutes
) : ContextAware, WithLifeCycle {

    override val context: Context get() = storage.context

    private data class Interval(var start: Instant, var end: Instant, val path: Path)

    private fun compareIntervals(first: Interval, second: Interval): Int {
        val startComparison = first.start.compareTo(second.start)
        if (startComparison != 0) return startComparison

        val endComparison = first.end.compareTo(second.end)
        if (endComparison != 0) return endComparison

        return first.path.compareTo(second.path)
    }

    private class IntervalNode(
        var interval: Interval,
        var left: IntervalNode? = null,
        var right: IntervalNode? = null,
        var height: Int = 1,
        var maxEnd: Instant = interval.end
    )


    private var root: IntervalNode? = null

    private val treeMutex: Mutex = Mutex()

    override var lifecycleState: LifecycleState = LifecycleState.STOPPED
        private set


    /**
     * Select row envelopes in [range]
     */
    public suspend fun selectEnvelopes(range: ClosedRange<Instant>): List<Envelope> {
        if(lifecycleState == LifecycleState.STOPPED) start()

        return search(range).sortedBy { it.start }.mapNotNull { operations.readEnvelope(it.path) }
    }

    /**
     * Select all rows in a given range
     */
    public suspend fun selectRows(range: ClosedRange<Instant>): Rows<Meta> {
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
    private suspend fun insert(path: Path): Interval? {
        val envelope = operations.readEnvelope(path) ?: return null
        val startTime = envelope.meta[RowEnvelopeMetaSpec.startTime]
        val endTime = envelope.meta[RowEnvelopeMetaSpec.endTime]

        if (startTime == null || endTime == null) {
            logger.warn { "Start or end time is not defined for envelope $path" }
            return null
        }

        val interval = Interval(
            start = startTime,
            end = endTime,
            path = path,
        )

        treeMutex.withLock {
            root = insert(root, interval)
        }

        return interval
    }


    private fun remove(interval: Interval) {
        root = remove(root, interval)
    }

    private suspend fun search(query: ClosedRange<Instant>): List<Interval> = treeMutex.withLock {
        buildList {
            search(root, query, this)
        }
    }

    // -------------------------
    // INSERT
    // -------------------------
    private fun insert(node: IntervalNode?, interval: Interval): IntervalNode {
        node ?: return IntervalNode(interval)

        val comparison = compareIntervals(interval, node.interval)
        when {
            comparison < 0 -> node.left = insert(node.left, interval)
            comparison > 0 -> node.right = insert(node.right, interval)
            else -> return node // the same file could be found both by the scan and by the monitor
        }

        update(node)
        return balance(node)
    }

    // -------------------------
    // REMOVE
    // -------------------------
    private fun remove(node: IntervalNode?, interval: Interval): IntervalNode? {
        node ?: return null

        val comparison = compareIntervals(interval, node.interval)
        when {
            comparison < 0 ->
                node.left = remove(node.left, interval)

            comparison > 0 ->
                node.right = remove(node.right, interval)

            else -> {
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

    private suspend fun removeIf(predicate: (Interval) -> Boolean) {
        treeMutex.withLock {
            val toRemove = mutableListOf<Interval>()
            search(root, predicate, toRemove)
            for (interval in toRemove) {
                remove(interval)
            }
        }
    }

    private var monitorJob: Job? = null

    /**
     * Rebuild the index from [dataDirectory] contents and wait for initial indexing to be complete
     */
    override suspend fun start(): Unit {

        lifecycleState = LifecycleState.STARTING

        treeMutex.withLock { root = null }

        // watch before the scan, so that files created during the scan are not missed
        val watchService = try {
            dataDirectory.watchFiles()
        } catch (ex: Throwable) {
            lifecycleState = LifecycleState.STOPPED
            throw ex
        }
        try {
            operations.envelopeFilesSequence(dataDirectory).forEach { (_, path) ->
                insert(path)
            }
            currentCoroutineContext().ensureActive()
        } catch (ex: Throwable) {
            watchService.close()
            // a stopped index is started again by the next query
            lifecycleState = LifecycleState.STOPPED
            throw ex
        }

        lifecycleState = LifecycleState.STARTED

        monitorJob = scope.launch(Dispatchers.IO) {

            val removedFiles = mutableListOf<Path>()

            val removalMutex: Mutex = Mutex()

            launchDirectoryMonitor(watchService) { kind, file ->
                // an overflow has no file, so the whole directory is scanned again; indexed files are not added twice
                val path = file?.let { dataDirectory.resolve(it) } ?: dataDirectory

                when (kind) {
                    ENTRY_CREATE, OVERFLOW -> {
                        operations.envelopeFilesSequence(path).forEach { (_, createdPath) ->
                            insert(createdPath)
                        }
                    }

                    ENTRY_DELETE -> removalMutex.withLock {
                        removedFiles.add(path)
                    }
                }
            }

            launch {
                while (isActive) {
                    delay(removeFilesCycleDuration)
                    val pathsToRemove = removalMutex.withLock {
                        removedFiles.toSet().also { removedFiles.clear() }
                    }
                    if (pathsToRemove.isNotEmpty()) {
                        removeIf { it.path in pathsToRemove }
                    }
                }
            }
        }.apply { invokeOnCompletion { watchService.close() } }
    }

    /**
     * Stop the directory monitor and wait for it to finish
     */
    override suspend fun stop() {
        monitorJob?.cancelAndJoin()
        monitorJob = null

        lifecycleState = LifecycleState.STOPPED
    }

}
