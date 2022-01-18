import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.storage.getPropertyHistory
import ru.mipt.npm.controls.xodus.XODUS_STORE_PROPERTY
import ru.mipt.npm.controls.xodus.XodusEventStorage
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getPropertyHistoryTest() = runTest {
        assertEquals(
            listOf(propertyChangedMessages[0]),
            getPropertyHistory(
            "virtual-car", "speed", XodusEventStorage, Meta {
                XODUS_STORE_PROPERTY put storeName
            })
        )
    }
}