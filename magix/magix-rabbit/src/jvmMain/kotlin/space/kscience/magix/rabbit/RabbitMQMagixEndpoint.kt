package space.kscience.magix.rabbit

import com.rabbitmq.client.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import space.kscience.magix.api.filter
import space.kscience.magix.rabbit.RabbitMQMagixEndpoint.Companion.DEFAULT_MAGIX_QUEUE_NAME

/**
 * A magix endpoint for RabbitMQ message broker
 */
public class RabbitMQMagixEndpoint(
    private val connection: Connection,
    private val queueName: String = DEFAULT_MAGIX_QUEUE_NAME,
) : MagixEndpoint, AutoCloseable {

    private val rabbitChannel by lazy {
        connection.createChannel().apply {
            queueDeclare(queueName, false, false, false, null)
        }
    }

    override fun subscribe(filter: MagixMessageFilter): Flow<MagixMessage> = callbackFlow {
        val deliverCallback = DeliverCallback { _: String, message: Delivery ->
            val magixMessage = MagixEndpoint.magixJson.decodeFromString(
                MagixMessage.serializer(), message.body.decodeToString()
            )
            launch {
                send(magixMessage)
            }
        }

        val cancelCallback: CancelCallback = CancelCallback {
            cancel("Rabbit consumer is closed")
        }

        val consumerTag = rabbitChannel.basicConsume(
            queueName,
            true,
            deliverCallback,
            cancelCallback
        )

        awaitClose {
            rabbitChannel.basicCancel(consumerTag)
        }
    }.filter(filter)

    override suspend fun broadcast(message: MagixMessage) {
        rabbitChannel.basicPublish(
            "",
            queueName,
            null,
            MagixEndpoint.magixJson.encodeToString(MagixMessage.serializer(), message).encodeToByteArray()
        )
    }

    override fun close() {
        rabbitChannel.close()
        connection.close()
    }

    public companion object {
        public const val DEFAULT_MAGIX_QUEUE_NAME: String = "magix"
    }
}

public fun MagixEndpoint.Companion.rabbit(
    address: String,
    queueName: String = DEFAULT_MAGIX_QUEUE_NAME,
): RabbitMQMagixEndpoint {
    val connection = ConnectionFactory().newConnection(address)
    return RabbitMQMagixEndpoint(connection, queueName)
}