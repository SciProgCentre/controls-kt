package space.kscience.controls.tagtable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import space.kscience.controls.tagtable.storage.launchDirectoryMonitor
import space.kscience.dataforge.context.Context
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DirectoryMonitorTest {
    @Test
    fun testCancellationStopsWaitingMonitor() = runTest(timeout = 60.seconds) {
        val context = Context("directory-monitor-test")
        val directory = Files.createTempDirectory("directory-monitor-test")
        val event = CompletableDeferred<Pair<WatchEvent.Kind<*>, Path>>()
        val monitor = context.launchDirectoryMonitor(directory) { kind, file -> event.complete(kind to file) }

        try {
            withContext(Dispatchers.Default) {
                val writer = launch {
                    //the watch service is registered inside the monitor, so keep creating files until one is reported
                    var index = 0
                    while (isActive) {
                        Files.createFile(directory.resolve("created-${index++}.txt"))
                        delay(100.milliseconds)
                    }
                }
                val (kind, file) = withTimeout(20.seconds) { event.await() }
                writer.cancelAndJoin()
                assertEquals(ENTRY_CREATE, kind)
                assertTrue(file.toString().startsWith("created-"), "unexpected event on $file")

                //let the monitor drain the pending events and block in take() again, which cancellation must interrupt
                delay(200.milliseconds)
                monitor.cancel()
                withTimeout(10.seconds) { monitor.join() }
            }
            assertTrue(context.isActive)
        } finally {
            monitor.cancel()
            context.close()
            Files.list(directory).use { files -> files.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun testMonitorCancelledBeforeStartClosesWatcher() = runTest {
        var closed = false
        val watcher = object : WatchService {
            override fun close() {
                closed = true
            }

            override fun poll(): WatchKey? = null
            override fun poll(timeout: Long, unit: TimeUnit): WatchKey? = null
            override fun take(): WatchKey = error("A cancelled monitor must not wait for events")
        }
        val cancelled = Job().also { it.cancel() }
        CoroutineScope(cancelled).launchDirectoryMonitor(watcher) { _, _ -> }.join()
        assertTrue(closed, "The watcher of a cancelled monitor was left open")
    }
}
