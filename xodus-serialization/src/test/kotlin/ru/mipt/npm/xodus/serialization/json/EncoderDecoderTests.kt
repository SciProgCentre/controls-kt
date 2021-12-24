package ru.mipt.npm.xodus.serialization.json

import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.magix.api.MagixMessage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import java.nio.file.Paths
import kotlin.test.assertEquals

internal class EncoderDecoderTests {
    companion object {
        private val storePath = Paths.get(".xodus_serialization_test")
        private val entityStore = PersistentEntityStores.newInstance(storePath.toString())

        @AfterAll
        @JvmStatic
        fun deleteDatabase() {
            entityStore.close()
            storePath.toFile().deleteRecursively()
        }

        @AfterEach
        fun clearDatabase() {
            entityStore.clear()
        }

        fun checkEncodingDecodingCorrectness(json: JsonObject) {
            val id = entityStore.encodeToEntity(json, "JsonObject")
            assertEquals(json, entityStore.decodeFromEntity(id))
        }

        fun checkEncodingDecodingCorrectness(jsons: List<JsonObject>) = jsons.forEach {
            checkEncodingDecodingCorrectness(it)
        }

    }

    @Test
    fun `encoder throw Illegal exception if input is not a JsonObject`() {
        assertThrows<IllegalStateException> {
            val json = JsonPrimitive(0)
            entityStore.encodeToEntity(json, "JsonPrimitive")
        }

        assertThrows<IllegalStateException> {
            val json = buildJsonArray {}
            entityStore.encodeToEntity(json, "JsonArray")
        }
    }

    @Test
    fun `correctly work with underlying JsonPrimitive`() {
        val jsonLong = buildJsonObject { put("value", 0) }
        val jsonDouble = buildJsonObject { put("value", 0.0) }
        val jsonBoolean = buildJsonObject { put("value", true) }
        val jsonString = buildJsonObject { put("value", "") }

        checkEncodingDecodingCorrectness(listOf(jsonLong, jsonDouble, jsonBoolean, jsonString))
    }

    @Test
    fun `correctly work with underlying JsonArray`() {
        checkEncodingDecodingCorrectness(buildJsonObject { putJsonArray("value") {
            add(buildJsonObject { put("value", 0) })
            add(buildJsonObject { put("value", 0.0) })
        } })
    }

    @Test
    fun `correctly work with underlying JsonObject`() {
        checkEncodingDecodingCorrectness(buildJsonObject {
            putJsonObject("value", { put("value", true) })
        })
    }

    @Test
    fun testMagixMessagePropertyChangedMessage() {
        val expectedMessage = MagixMessage(
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
            user = buildJsonObject { put("name", "SCADA") }
        )

        val id = entityStore.encodeToEntity(expectedMessage, "MagixMessage")
        assertEquals(expectedMessage, entityStore.decodeFromEntity<MagixMessage<PropertyChangedMessage>>(id))
    }
}