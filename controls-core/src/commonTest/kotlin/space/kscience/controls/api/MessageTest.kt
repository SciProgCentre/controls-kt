package space.kscience.controls.api

import kotlinx.serialization.json.Json
import space.kscience.controls.asMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class MessageTest {
    @Test
    fun messageSerialization() {
        val changedMessage = PropertyChangedMessage(Clock.System.now(),"test", 22.0.asMeta())
        val json = Json.encodeToString(changedMessage)
        val reconstructed: PropertyChangedMessage = Json.decodeFromString(json)
        assertEquals(changedMessage.time, reconstructed.time)
    }
}