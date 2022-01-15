import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.storage.synchronous.getPropertyHistory
import ru.mipt.npm.controls.xodus.DefaultSynchronousXodusClientFactory
import ru.mipt.npm.controls.xodus.XODUS_STORE_PROPERTY
import ru.mipt.npm.xodus.serialization.json.encodeToEntity
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import java.io.File

internal class PropertyHistoryTest {
    companion object {
        private val storeName = ".property_history_test"
        private val entityStore = PersistentEntityStores.newInstance(storeName)

        private val propertyChangedMessages = listOf(
            PropertyChangedMessage(
                "speed",
                Meta.EMPTY,
                time = Instant.fromEpochMilliseconds(1000),
                sourceDevice = Name.of("virtual-car")
            ),
            PropertyChangedMessage(
                "acceleration",
                Meta.EMPTY,
                time = Instant.fromEpochMilliseconds(1500),
                sourceDevice = Name.of("virtual-car")
            ),
            PropertyChangedMessage(
                "speed",
                Meta.EMPTY,
                time = Instant.fromEpochMilliseconds(2000),
                sourceDevice = Name.of("magix-virtual-car")
            )
        )

        @BeforeAll
        @JvmStatic
        fun createEntities() {
            propertyChangedMessages.forEach {
                entityStore.encodeToEntity<DeviceMessage>(it, "DeviceMessage")
            }
            entityStore.close()
        }

        @AfterAll
        @JvmStatic
        fun deleteDatabase() {
            File(storeName).deleteRecursively()
        }
    }

    @Test
    fun getPropertyHistoryTest() {
        assertEquals(listOf(propertyChangedMessages[0]), getPropertyHistory(
            "virtual-car", "speed", DefaultSynchronousXodusClientFactory, Meta {
                XODUS_STORE_PROPERTY put storeName
            }))
    }
}