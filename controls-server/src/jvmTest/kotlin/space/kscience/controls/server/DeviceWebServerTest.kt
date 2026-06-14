/*
 * LLM generated code: tests for deviceWebServer.kt
 */
package space.kscience.controls.server

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import space.kscience.controls.api.*
import space.kscience.controls.asMeta
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.install
import space.kscience.controls.spec.Device
import space.kscience.controls.spec.DevicePropertySpec
import space.kscience.controls.spec.reader
import space.kscience.controls.spec.writer
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.names.Name
import space.kscience.magix.api.MagixEndpoint
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class DeviceWebServerTest {

    @Test
    fun testRoutes() = testApplication {
        val context = Context("test")
        val manager = context.request(DeviceManager)

        val propSpec = DevicePropertySpec(MetaConverter.double, PropertyDescriptor("prop", mutable = true))

        manager.install("test", Device(context) {

            var value = 1.0
            reader(propSpec) { value }
            writer(propSpec) { value = it }
        })

        application {
            deviceManagerModule(manager)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
        }

        // Test dashboard
        val dashboardResponse = client.get("/dashboard")
        assertEquals(HttpStatusCode.OK, dashboardResponse.status)
        assertTrue(dashboardResponse.bodyAsText().contains("Device server dashboard"))

        // Test tree
        val treeResponse = client.get("/tree?expand=true")
        assertEquals(HttpStatusCode.OK, treeResponse.status)
        val treeJson = treeResponse.bodyAsText()
        val json = Json.parseToJsonElement(treeJson).jsonObject
        assertTrue(json.containsKey("test"))

        // Test get property
        val getResponse = client.get("/devices/test/get/prop")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val messages = Json.decodeFromString<List<DeviceMessage>>(getResponse.bodyAsText())
        assertTrue(messages.isNotEmpty())

        // Test set property
        val setResponse = client.post("/devices/test/set/prop") {
            contentType(ContentType.Application.Json)
            setBody(JsonPrimitive(2.0))
        }
        assertEquals(HttpStatusCode.OK, setResponse.status)

        // Test send
        val sendResponse = client.post("/send") {
            contentType(ContentType.Application.Json)
            val message = PropertyGetMessage(
                property = "prop",
                targetDevice = Name.parse("test"),
                time = Clock.System.now()
            )
            setBody(MagixEndpoint.magixJson.encodeToString(DeviceMessage.serializer(), message))
        }
        assertEquals(HttpStatusCode.OK, sendResponse.status)

        // Test OpenAPI
        val openApiResponse = client.get("/openAPI")
        assertEquals(HttpStatusCode.OK, openApiResponse.status)

        // Test WebSocket subscribe
        client.webSocket("/devices/test/subscribe/prop") {
            manager.resolveDevice("test").writeProperty("prop", 3.0.asMeta())
            var message: DeviceMessage?
            withTimeout(1.seconds) {
                while (true) {
                    val frame = incoming.receive()
                    assertTrue(frame is Frame.Text)
                    message = MagixEndpoint.magixJson.decodeFromString<DeviceMessage>(frame.readText())
                    if (message is PropertyChangedMessage && message.property == "prop" && message.value.double == 3.0) {
                        break
                    }
                }
                assertEquals("prop", message.property)
                assertEquals(3.0, message.value.double)
            }
        }

    }
}
