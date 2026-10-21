/*
 * LLM generated code: Tests for FileDeviceMessageStorage covering write, read, filtering, rotation, and retention.
 */
package space.kscience.controls.storage

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import space.kscience.controls.api.DeviceLogMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.parseAsName
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FileDeviceMessageStorageTest {

    @TempDir
    lateinit var tempDir: Path

    private class TestClock(var current: Instant = Clock.System.now()) : Clock {
        override fun now(): Instant = current
    }

    @Test
    fun testWriteAndReadSingleMessage() = runBlocking {
        val storage = FileDeviceMessageStorage(tempDir)
        val message = DeviceLogMessage(
            time = Instant.parse("2026-10-21T10:01:00Z"),
            message = "Single test log message",
            sourceDevice = "device.sensor.a".parseAsName(),
            targetDevice = "device.controller".parseAsName()
        )

        storage.write(message)

        val messages = storage.read().toList()
        assertEquals(1, messages.size)
        val readMessage = messages.first() as DeviceLogMessage
        assertEquals(message.message, readMessage.message)
        assertEquals(message.time, readMessage.time)
        assertEquals(message.sourceDevice, readMessage.sourceDevice)
        assertEquals(message.targetDevice, readMessage.targetDevice)
    }

    @Test
    fun testEventTypeAndDeviceFiltering() = runBlocking {
        val storage = FileDeviceMessageStorage(tempDir)
        val source1 = "plant.subsystem.sensor1".parseAsName()
        val source2 = "plant.subsystem.sensor2".parseAsName()
        val target1 = "plant.controller1".parseAsName()
        val target2 = "plant.controller2".parseAsName()

        val log1 = DeviceLogMessage(
            time = Instant.parse("2026-10-21T10:00:00Z"),
            message = "Log 1",
            sourceDevice = source1,
            targetDevice = target1
        )
        val log2 = DeviceLogMessage(
            time = Instant.parse("2026-10-21T10:01:00Z"),
            message = "Log 2",
            sourceDevice = source2,
            targetDevice = target2
        )
        val prop1 = PropertyChangedMessage(
            time = Instant.parse("2026-10-21T10:02:00Z"),
            property = "temperature",
            value = Meta(25.5),
            sourceDevice = source1,
            targetDevice = target2
        )

        storage.writeAll(listOf(log1, log2, prop1))

        // Read all
        val allMessages = storage.read().toList()
        assertEquals(3, allMessages.size)

        // Filter by event type
        val logs = storage.read<DeviceLogMessage>().toList()
        assertEquals(2, logs.size)
        assertEquals(listOf("Log 1", "Log 2"), logs.map { it.message })

        val props = storage.read<PropertyChangedMessage>().toList()
        assertEquals(1, props.size)
        assertEquals("temperature", props.first().property)

        // Filter by source device
        val source1Messages = storage.read(sourceDevice = source1).toList()
        assertEquals(2, source1Messages.size)
        assertTrue(source1Messages.all { it.sourceDevice == source1 })

        // Filter by target device
        val target1Messages = storage.read(targetDevice = target1).toList()
        assertEquals(1, target1Messages.size)
        assertEquals(log1.message, (target1Messages.first() as DeviceLogMessage).message)

        // Filter by both source and target device
        val source1Target2Messages = storage.read(sourceDevice = source1, targetDevice = target2).toList()
        assertEquals(1, source1Target2Messages.size)
        assertEquals(prop1.property, (source1Target2Messages.first() as PropertyChangedMessage).property)
    }

    @Test
    fun testTimeRangeFiltering() = runBlocking {
        val storage = FileDeviceMessageStorage(tempDir)
        val t1 = Instant.parse("2026-10-21T10:00:00Z")
        val t2 = Instant.parse("2026-10-21T10:10:00Z")
        val t3 = Instant.parse("2026-10-21T10:20:00Z")
        val t4 = Instant.parse("2026-10-21T10:30:00Z")

        val messages = listOf(t1, t2, t3, t4).mapIndexed { index, time ->
            DeviceLogMessage(
                time = time,
                message = "Message $index",
                sourceDevice = "device.sensor".parseAsName()
            )
        }
        storage.writeAll(messages)

        val inRange = storage.read(range = t2..t3).toList().sortedBy { it.time }
        assertEquals(2, inRange.size)
        assertEquals(listOf(t2, t3), inRange.map { it.time })
    }

    @Test
    fun testFileRotationAndMultipleFiles() = runBlocking {
        val startTime = Instant.parse("2026-10-21T10:00:00Z")
        val clock = TestClock(startTime)
        val storage = FileDeviceMessageStorage(
            directory = tempDir,
            maxMessagesPerFile = 2,
            clock = clock
        )

        // Write 2 messages for first file
        storage.write(DeviceLogMessage(time = clock.now(), message = "Msg 0", sourceDevice = "device".parseAsName()))
        clock.current += 1.seconds
        storage.write(DeviceLogMessage(time = clock.now(), message = "Msg 1", sourceDevice = "device".parseAsName()))

        // Advance clock so next file gets a distinct name
        clock.current += 1.minutes

        // Write 2 more messages triggering second file
        storage.write(DeviceLogMessage(time = clock.now(), message = "Msg 2", sourceDevice = "device".parseAsName()))
        clock.current += 1.seconds
        storage.write(DeviceLogMessage(time = clock.now(), message = "Msg 3", sourceDevice = "device".parseAsName()))

        // Advance clock for third file
        clock.current += 1.minutes
        storage.write(DeviceLogMessage(time = clock.now(), message = "Msg 4", sourceDevice = "device".parseAsName()))

        val files = tempDir.listDirectoryEntries("*.jsonl")
        assertEquals(3, files.size)

        val readMessages = storage.read().toList().sortedBy { it.time }
        assertEquals(5, readMessages.size)
        assertEquals((0..4).map { "Msg $it" }, readMessages.map { (it as DeviceLogMessage).message })
    }

    @Test
    fun testIncrementalIndexingAndReopen() = runBlocking {
        val storage1 = FileDeviceMessageStorage(tempDir)
        val msg1 = DeviceLogMessage(
            time = Instant.parse("2026-10-21T10:00:00Z"),
            message = "First write",
            sourceDevice = "device".parseAsName()
        )
        storage1.write(msg1)

        // Read once
        assertEquals(1, storage1.read().toList().size)

        // Write more messages to the same open file and read again to test incremental indexing
        val msg2 = DeviceLogMessage(
            time = Instant.parse("2026-10-21T10:01:00Z"),
            message = "Second write",
            sourceDevice = "device".parseAsName()
        )
        storage1.write(msg2)

        val messagesAfterAppend = storage1.read().toList().sortedBy { it.time }
        assertEquals(2, messagesAfterAppend.size)
        assertEquals(listOf("First write", "Second write"), messagesAfterAppend.map { (it as DeviceLogMessage).message })

        // Reopen new storage instance pointing to same directory
        val storage2 = FileDeviceMessageStorage(tempDir)
        val messagesFromReopened = storage2.read().toList().sortedBy { it.time }
        assertEquals(2, messagesFromReopened.size)
        assertEquals(listOf("First write", "Second write"), messagesFromReopened.map { (it as DeviceLogMessage).message })
    }

    @Test
    fun testHistoryDepthRetention() = runBlocking {
        val baseTime = Clock.System.now()
        val clock = TestClock(baseTime)
        val storage = FileDeviceMessageStorage(
            directory = tempDir,
            historyDepth = 1.hours,
            clock = clock
        )

        val oldMessage = DeviceLogMessage(
            time = baseTime - 2.hours,
            message = "Old message",
            sourceDevice = "device".parseAsName()
        )
        storage.write(oldMessage)

        // Explicitly set the file modification time to 2 hours ago
        val oldFile = tempDir.listDirectoryEntries("*.jsonl").first()
        Files.setLastModifiedTime(
            oldFile,
            FileTime.from(java.time.Instant.ofEpochMilli((baseTime - 2.hours).toEpochMilliseconds()))
        )

        // Advance clock for the new message
        clock.current = baseTime
        val newStorage = FileDeviceMessageStorage(
            directory = tempDir,
            historyDepth = 1.hours,
            clock = clock
        )
        val newMessage = PropertyChangedMessage(
            time = baseTime,
            property = "status",
            value = Meta("active"),
            sourceDevice = "device".parseAsName()
        )
        newStorage.write(newMessage)

        // Read with historyDepth = 1 hour should exclude the old file
        val activeMessages = newStorage.read().toList()
        assertEquals(1, activeMessages.size)
        assertTrue(activeMessages.first() is PropertyChangedMessage)
    }

    @Test
    fun testPropertyHistory() = runBlocking {
        val storage = FileDeviceMessageStorage(tempDir)
        val t1 = Instant.parse("2026-10-21T10:00:00Z")
        val t2 = Instant.parse("2026-10-21T10:05:00Z")
        val t3 = Instant.parse("2026-10-21T10:10:00Z")

        val p1 = PropertyChangedMessage(
            time = t1,
            property = "voltage",
            value = Meta(12.0),
            sourceDevice = "device".parseAsName()
        )
        val p2 = PropertyChangedMessage(
            time = t2,
            property = "current",
            value = Meta(1.5),
            sourceDevice = "device".parseAsName()
        )
        val p3 = PropertyChangedMessage(
            time = t3,
            property = "voltage",
            value = Meta(12.5),
            sourceDevice = "device".parseAsName()
        )

        storage.writeAll(listOf(p1, p2, p3))

        val voltageHistory = storage.propertyHistory("voltage", MetaConverter.double)
            .flowHistory(t1, t3)
            .toList()
            .sortedBy { it.time }

        assertEquals(2, voltageHistory.size)
        assertEquals(12.0, voltageHistory[0].value)
        assertEquals(t1, voltageHistory[0].time)
        assertEquals(12.5, voltageHistory[1].value)
        assertEquals(t3, voltageHistory[1].time)
    }

    @Test
    fun testUtf8MultiByteMessages() = runBlocking {
        val storage = FileDeviceMessageStorage(tempDir)
        val t1 = Instant.parse("2026-10-21T10:00:00Z")
        val t2 = Instant.parse("2026-10-21T10:01:00Z")

        val m1 = DeviceLogMessage(
            time = t1,
            message = "Привет, мир! 🚀",
            sourceDevice = "тест.устройство".parseAsName()
        )
        val m2 = DeviceLogMessage(
            time = t2,
            message = "Temperature: 25.5 °C (Ω ≈ 100)",
            sourceDevice = "тест.устройство".parseAsName()
        )

        storage.writeAll(listOf(m1, m2))

        val read = storage.read().toList().sortedBy { it.time }
        assertEquals(2, read.size)
        assertEquals(m1.message, (read[0] as DeviceLogMessage).message)
        assertEquals(m2.message, (read[1] as DeviceLogMessage).message)
    }
}
