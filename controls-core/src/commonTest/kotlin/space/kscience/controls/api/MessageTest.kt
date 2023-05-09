package space.kscience.controls.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import space.kscience.controls.spec.asMeta
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageTest {
    @Test
    fun messageSerialization() {
        val changedMessage = PropertyChangedMessage("test", 22.0.asMeta())
        val json = Json.encodeToString(changedMessage)
        val reconstructed: PropertyChangedMessage = Json.decodeFromString(json)
        assertEquals(changedMessage.time, reconstructed.time)
    }
}