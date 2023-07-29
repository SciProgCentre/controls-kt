package space.ksceince.magix.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import java.util.*

/**
 * MQTT5 endpoint for magix.
 *
 * @param broadcastTopicBuilder defines how the topic is constructed from broadcast message structure.
 * By default, use `magix/<target>` topic if target is present and `magix` if it is not.
 * @param subscribeTopicBuilder defines how the topic is constructed from the filter.
 * By default, uses `magix/<target>` if only a single target is presented and `magix/#` otherwise.
 */
public class MqttMagixEndpoint(
    serverHost: String,
    clientId: String = UUID.randomUUID().toString(),
    private val broadcastTopicBuilder: (MagixMessage) -> String = defaultBroadcastTopicBuilder,
    private val subscribeTopicBuilder: (MagixMessageFilter) -> String = defaultSubscribeTopicBuilder,
    private val qos: MqttQos = MqttQos.AT_LEAST_ONCE,
    private val clientConfig: Mqtt5ClientBuilder.() -> Mqtt5ClientBuilder = { this },
) : MagixEndpoint, AutoCloseable {

    private val client: Mqtt5AsyncClient by lazy {
        MqttClient.builder()
            .identifier(clientId)
            .serverHost(serverHost)
            .useMqttVersion5()
            .run(clientConfig)
            .buildAsync()
    }

    private val connection by lazy {
        client.connect()
    }

    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = callbackFlow {
        connection.await()
        client.subscribeWith()
            .topicFilter(subscribeTopicBuilder(filter))
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
        client.publishWith().topic(broadcastTopicBuilder(message)).qos(qos).payload(
            MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message).encodeToByteArray()
        ).send()
    }

    override fun close() {
        client.disconnect()
    }

    public companion object {
        public const val DEFAULT_MAGIX_TOPIC_NAME: String = "magix"

        //TODO add target name escaping

        internal val defaultBroadcastTopicBuilder: (MagixMessage) -> String = { message ->
            message.targetEndpoint?.let { "$DEFAULT_MAGIX_TOPIC_NAME/it" } ?: DEFAULT_MAGIX_TOPIC_NAME
        }

        internal val defaultSubscribeTopicBuilder: (MagixMessageFilter) -> String = { filter ->
            if (filter.target?.size == 1) {
                "$DEFAULT_MAGIX_TOPIC_NAME/${filter.target!!.first()}"
            } else {
                "$DEFAULT_MAGIX_TOPIC_NAME/#"
            }
        }
    }
}