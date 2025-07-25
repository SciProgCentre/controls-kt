import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.read
import space.kscience.controls.xodus.XodusDeviceMessageStorage
import space.kscience.controls.xodus.writeMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.asName
import java.nio.file.Files
import kotlin.time.Instant

internal class PropertyHistoryTest {
    companion object {
        val storeFile = Files.createTempDirectory("controls-xodus").toFile()


        private val propertyChangedMessages = listOf(
            PropertyChangedMessage(
                time = Instant.fromEpochMilliseconds(1000),
                "speed",
                Meta.EMPTY,
                sourceDevice = Name.of("virtual-car")
            ),
            PropertyChangedMessage(
                time = Instant.fromEpochMilliseconds(1500),
                "acceleration",
                Meta.EMPTY,
                sourceDevice = Name.of("virtual-car")
            ),
            PropertyChangedMessage(
                time = Instant.fromEpochMilliseconds(2000),
                "speed",
                Meta.EMPTY,
                sourceDevice = Name.of("magix-virtual-car")
            )
        )

        @BeforeAll
        @JvmStatic
        fun createEntities() {
            PersistentEntityStores.newInstance(storeFile).use {
                it.executeInTransaction { transaction ->
                    propertyChangedMessages.forEach { message ->
                        transaction.writeMessage(message)
                    }
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun deleteDatabase() {
            storeFile.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getPropertyHistoryTest() = runTest {
        PersistentEntityStores.newInstance(storeFile).use { entityStore ->
            XodusDeviceMessageStorage(entityStore).use { storage ->
                assertEquals(
                    propertyChangedMessages[0],
                    storage.read<PropertyChangedMessage>(
                        sourceDevice = "virtual-car".asName()
                    ).first { it.property == "speed" }
                )
            }
        }
    }
}