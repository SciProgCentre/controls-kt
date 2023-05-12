package space.ksceince.magix.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import java.util.*


public class MqttMagixEndpoint(
    serverHost: String,
    clientId: String = UUID.randomUUID().toString(),
    public val topic: String = DEFAULT_MAGIX_TOPIC_NAME,
    public val qos: MqttQos = MqttQos.AT_LEAST_ONCE,
) : MagixEndpoint, AutoCloseable {

    private val client: Mqtt5AsyncClient by lazy {
        MqttClient.builder()
            .identifier(clientId)
            .serverHost(serverHost)
            .useMqttVersion5()
            .buildAsync()
    }

    private val connection by lazy {
        client.connect()
    }

    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = callbackFlow {
        connection.await()
        client.subscribeWith()
            .topicFilter(topic)
            .qos(qos)
            .callback { published ->
                val message = MagixEndpoint.magixJson.decodeFromString(
                    MagixMessage.serializer(),
                    published.payloadAsBytes.decodeToString()
                )
                trySendBlocking(message)
            }
            .send()

        awaitClose {
            client.disconnect()
        }
    }

    override suspend fun broadcast(message: MagixMessage) {
        connection.await()
        client.publishWith().topic(topic).qos(qos).payload(
            MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message).encodeToByteArray()
        ).send()
    }

    override fun close() {
        client.disconnect()
    }

    public companion object {
        public const val DEFAULT_MAGIX_TOPIC_NAME: String = "magix"
    }
}