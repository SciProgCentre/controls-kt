package space.kscience.controls.storage.exposed


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import java.io.File
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.time.measureTime

class TimescaleStressTest {

    companion object {
        private val dataDir = File("data/timescale-data").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        private lateinit var timescale: PostgreSQLContainer

        @BeforeAll
        @JvmStatic
        fun startContainer() {

            val image: DockerImageName = DockerImageName.parse("timescale/timescaledb:latest-pg16")
                .asCompatibleSubstituteFor("postgres")

            timescale = PostgreSQLContainer(image).apply {
                withDatabaseName("test")
                withUsername("test")
                withPassword("test")
                withFileSystemBind(dataDir.absolutePath, "/var/lib/postgresql/data", BindMode.READ_WRITE)
                start()
            }
            val size = getDirectorySize(dataDir)
            println("[DEBUG_LOG] Storage size before test: ${size / 1024 / 1024} MB ($size bytes)")
        }

        @AfterAll
        @JvmStatic
        fun stopContainer() {
            timescale.stop()
            val size = getDirectorySize(dataDir)
            println("[DEBUG_LOG] Storage size after test: ${size / 1024 / 1024} MB ($size bytes)")
        }

        private fun getDirectorySize(directory: File): Long {
            return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    @Test
    @Ignore("For manual testing only")
    fun testReadWrite(): Unit = runBlocking(Dispatchers.IO) {
        val database = Database.connect(
            url = timescale.jdbcUrl + "&reWriteBatchedInserts=true",
            driver = "org.postgresql.Driver",
            user = timescale.username,
            password = timescale.password
        )

        // The storage initializes the table in its init block
        val storage = ExposedDeviceMessageStorage(database, pageSize = 50000)

        // LLM generated code: converting the table to a TimescaleDB hypertable
        transaction(database) {
            exec("SELECT create_hypertable('DeviceMessages', 'time', if_not_exists => TRUE);")
        }

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
            events.chunked(5000).forEach { chunk ->
                storage.writeAll(chunk)
            }
        }.also {
            println("[DEBUG_LOG] Write time: $it")
        }

        measureTime {
            val result = storage.read().toList()
            assertEquals(messages, result.size)
        }.also {
            println("[DEBUG_LOG] Read time: $it")
        }
    }
}
