package space.kscience.controls.storage.exposed

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import space.kscience.controls.api.DeviceLogMessage
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Instant

class ExposedDeviceMessageStorageTest {

    companion object {
        lateinit var database: Database

        @BeforeAll
        @JvmStatic
        fun setup() {
            database = Database.connect("jdbc:h2:mem:messages;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            // H2 mem database is closed when the last connection is closed.
        }
    }

    @BeforeEach
    fun clear() {
        transaction(database) {
            SchemaUtils.drop(ExposedDeviceMessageStorage.DeviceMessages)
            SchemaUtils.create(ExposedDeviceMessageStorage.DeviceMessages)
        }
    }

    @Test
    fun testWriteRead() = runTest {
        val storage = ExposedDeviceMessageStorage(database)
        val message = DeviceLogMessage(
            time = Instant.fromEpochMilliseconds(1000),
            message = "Test message",
            sourceDevice = Name.parse("source")
        )

        storage.write(message)

        val messages = storage.read().toList()
        assertEquals(1, messages.size)
        assertEquals("Test message", (messages[0] as DeviceLogMessage).message)
    }

    @Test
    fun testFiltering() = runTest {
        val storage = ExposedDeviceMessageStorage(database)

        val message1 = DeviceLogMessage(
            time = Instant.fromEpochMilliseconds(1000),
            message = "Message 1",
            sourceDevice = Name.parse("source1"),
            targetDevice = Name.parse("target1")
        )
        val message2 = DeviceLogMessage(
            time = Instant.fromEpochMilliseconds(2000),
            message = "Message 2",
            sourceDevice = Name.parse("source2"),
            targetDevice = Name.parse("target2")
        )

        storage.write(message1)
        storage.write(message2)

        val source1Messages = storage.read(
            eventType = DeviceMessage.serialNameFor<DeviceLogMessage>(),
            sourceDevice = Name.parse("source1")
        ).toList()
        assertEquals(1, source1Messages.size)
        assertEquals("Message 1", (source1Messages[0] as DeviceLogMessage).message)

        val target2Messages = storage.read(
            eventType = DeviceMessage.serialNameFor<DeviceLogMessage>(),
            targetDevice = Name.parse("target2")
        ).toList()
        assertEquals(1, target2Messages.size)
        assertEquals("Message 2", (target2Messages[0] as DeviceLogMessage).message)
    }

    @Test
    fun testLargeBatch() = runTest {
        val storage = ExposedDeviceMessageStorage(database, pageSize = 1000)
        val count = 9500
        val messages = List(count) { i ->
            PropertyChangedMessage(
                time = Instant.fromEpochMilliseconds(i.toLong()),
                property = "prop",
                value = Meta(i.toLong()),
                sourceDevice = Name.parse("source")
            )
        }

        messages.forEach { storage.write(it) }

        val readMessages = storage.read().toList()

        assertContentEquals(messages.sortedByDescending { it.time }, readMessages)
    }
}
