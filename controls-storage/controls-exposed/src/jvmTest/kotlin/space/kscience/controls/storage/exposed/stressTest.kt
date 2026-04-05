package space.kscience.controls.storage.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.time.measureTime


class StressTest {

    @Test
    public fun testReadWrite(): Unit = runBlocking(Dispatchers.IO) {

        val database = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

        val storage = ExposedDeviceMessageStorage(database)

        val messages = 100_000


        val events = List(messages) { i ->
            PropertyChangedMessage(
                time = Instant.fromEpochMilliseconds(i.toLong()),
                property = "prop",
                value = Meta(i.toLong()),
                sourceDevice = Name.parse("source")
            )
        }

        measureTime {
            events.chunked(1000).forEach { chunk ->
                storage.writeAll(chunk)
            }

        }.also {
            println("Write time: $it")
        }

        measureTime {
            val result = storage.readAll().toList()
            assertEquals(messages, result.size)
        }.also {
            println("Read time: $it")
        }

    }
}