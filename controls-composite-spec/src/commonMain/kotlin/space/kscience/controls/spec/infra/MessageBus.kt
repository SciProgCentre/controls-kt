package space.kscience.controls.spec.infra

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import space.kscience.controls.api.Message
import space.kscience.controls.spec.config.MessageBusConfig
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Logger
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.context.warn
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.MagixMessageFilter as MagixApiMessageFilter
import space.kscience.magix.api.send as magixSend

/**
 * Interface for message tracing.
 */
public fun interface MessageTracer {
    public fun onPublish(message: Message, bus: MessageBus)
}

/**
 * Interface for a message bus.
 */
public interface MessageBus : AutoCloseable {
    public fun subscribe(filter: MessageFilter = MessageFilter.ALL): Flow<Message>
    public suspend fun publish(message: Message)
    override fun close()
}

/**
 * Factory for creating [MessageBus] instances.
 */
public object MessageBusFactory {
    public fun create(
        context: Context,
        magixSourceEndpoint: String = MessageBusConfig.Factory.Defaults.DEFAULT_MAGIX_SOURCE_ENDPOINT,
        inMemoryBufferSize: Int = MessageBusConfig.Factory.Defaults.IN_MEMORY_MESSAGE_BUS_BUFFER_CAPACITY,
        tracer: MessageTracer? = null
    ): MessageBus {
        val magixEndpoint = context.plugins.filterIsInstance<MagixEndpoint>().firstOrNull()
        return if (magixEndpoint != null) {
            MagixMessageBus(magixEndpoint, magixSourceEndpoint, tracer, context.logger)
        } else {
            InMemoryMessageBus(inMemoryBufferSize, tracer, context.logger)
        }
    }
}

/**
 * A [MessageBus] implementation bridging to a [MagixEndpoint].
 */
public class MagixMessageBus(
    public val magixEndpoint: MagixEndpoint,
    public val sourceEndpoint: String,
    private val tracer: MessageTracer? = null,
    private val logger: Logger? = null
) : MessageBus {
    @OptIn(ExperimentalSerializationApi::class)
    private val internalMessageFormat = MagixFormat(
        Message.serializer(),
        setOf(Message.serializer().descriptor.serialName)
    )

    override fun subscribe(filter: MessageFilter): Flow<Message> =
        magixEndpoint.subscribe(
            MagixApiMessageFilter(format = setOf(internalMessageFormat.defaultFormat))
        ).mapNotNull { magixMsg ->
            try {
                val decodedInternalMessage = MagixEndpoint.magixJson.decodeFromJsonElement(
                    Message.serializer(),
                    magixMsg.payload
                )
                if (filter.accepts(decodedInternalMessage)) decodedInternalMessage else null
            } catch (e: Exception) {
                logger?.error(e) { "Failed to decode Magix payload to internal Message: ${magixMsg.payload}" }
                null
            }
        }

    override suspend fun publish(message: Message) {
        tracer?.onPublish(message, this)
        try {
            magixEndpoint.magixSend(internalMessageFormat, message, source = this.sourceEndpoint, target = message.targetDevice?.toString())
        } catch (e: Exception) {
            logger?.error(e) { "Failed to send internal Message via Magix: ${e.message}" }
        }
    }

    override fun close() {
        logger?.info { "MagixMessageBus for endpoint '$sourceEndpoint' closed (underlying MagixEndpoint not closed by this adapter)." }
    }
}

/**
 * An in-memory implementation of [MessageBus].
 */
public class InMemoryMessageBus(
    bufferCapacity: Int = MessageBusConfig.Factory.Defaults.IN_MEMORY_MESSAGE_BUS_BUFFER_CAPACITY,
    private val tracer: MessageTracer? = null,
    private val logger: Logger? = null
) : MessageBus {
    private val sharedFlow = MutableSharedFlow<Message>(
        replay = 0,
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun subscribe(filter: MessageFilter): Flow<Message> =
        sharedFlow.asSharedFlow().filter { filter.accepts(it) }

    override suspend fun publish(message: Message) {
        tracer?.onPublish(message, this)
        if (!sharedFlow.tryEmit(message)) {
            logger?.warn { "InMemoryMessageBus buffer overflow or slow subscriber, message dropped: ${message.messageType} from ${message.sourceDevice}" }
        }
    }

    override fun close() {
        logger?.info { "InMemoryMessageBus closed." }
    }
}