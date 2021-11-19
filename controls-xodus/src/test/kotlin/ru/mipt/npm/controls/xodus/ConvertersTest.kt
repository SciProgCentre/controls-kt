package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import java.io.File
import kotlin.test.assertEquals

internal class ConvertersTest {
    companion object {
        private val storeName = ".converters_test"
        private val entityStore = PersistentEntityStores.newInstance(storeName)
        private val expectedMessage = MagixMessage(
            "dataforge",
            "dataforge",
            PropertyChangedMessage(
                "acceleration",
                Meta {
                    "x" put 3.0
                    "y" put 9.0
                },
                Name.parse("virtual-car"),
                Name.parse("magix-virtual-car"),
                time = Instant.fromEpochMilliseconds(1337)
            ),
            "magix-virtual-car",
            user = JsonObject(content = mapOf(Pair("name", JsonPrimitive("SCADA"))))
        )

        @BeforeAll
        @JvmStatic
        fun createEntities() {
            entityStore.executeInTransaction {
                expectedMessage.toEntity(it)
            }
        }

        @AfterAll
        @JvmStatic
        fun deleteDatabase() {
            entityStore.close()
            File(storeName).deleteRecursively()
        }
    }

    @Test
    fun testMagixMessageAndPropertyChangedMessageConverters() {
        assertEquals(expectedMessage, entityStore.computeInReadonlyTransaction {
            it.getAll("MagixMessage").first?.toMagixMessage()
        }!!)
    }
}
