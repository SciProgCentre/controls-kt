package space.kscience.magix.mqtt

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.slf4j.event.Level
import org.testcontainers.hivemq.HiveMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import space.ksceince.magix.mqtt.MqttMagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.send
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@Testcontainers
@EnabledIfSystemProperty(named = "controls.test.containers", matches = "true")
class MqttEndpointTest {

    @Container
    val hivemqCe: HiveMQContainer = HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2024.3"))
        .withLogLevel(Level.DEBUG)


    @Test
    fun magixMqtt() = runTest(timeout = 2.seconds) {
        val endpoint1 = MqttMagixEndpoint(
            hivemqCe.host,
            hivemqCe.mqttPort
        )

        val endpoint2 = MqttMagixEndpoint(
            hivemqCe.host,
            hivemqCe.mqttPort
        )

        val result = CompletableDeferred<MagixMessage>()

        val receiveJob = endpoint2.subscribe().onEach {
            println(it)
            result.complete(it)
        }.launchIn(this)

        endpoint1.send(
            MagixMessage(
                format = "test",
                payload = JsonPrimitive("Hello MQTT!"),
                sourceEndpoint = "test"
            )
        )

        result.await().let {
            assertEquals("Hello MQTT!", it.payload.jsonPrimitive.content)
            assertEquals(
                "test",
                it.sourceEndpoint
            )
        }

        receiveJob.cancel()
        endpoint1.close()
        endpoint2.close()
    }
}