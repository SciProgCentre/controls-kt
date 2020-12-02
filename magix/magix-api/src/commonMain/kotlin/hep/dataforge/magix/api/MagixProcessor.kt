package hep.dataforge.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

public fun interface MagixProcessor {
    public fun process(endpoint: MagixEndpoint): Job

    public companion object {
        /**
         * A converter from one (or several) format to another. It captures all events with the given filter then transforms it
         * with given [transformer] and sends back to the loop with given [outputFormat].
         *
         * If [newOrigin] is not null, it is used as a replacement for old [MagixMessage.origin] tag.
         */
        public fun <T : Any, R : Any> convert(
            scope: CoroutineScope,
            filter: MagixMessageFilter,
            outputFormat: String,
            inputSerializer: KSerializer<T>,
            outputSerializer: KSerializer<R>,
            newOrigin: String? = null,
            transformer: suspend (T) -> R,
        ): MagixProcessor = MagixProcessor { endpoint ->
            endpoint.subscribe(inputSerializer, filter).onEach { message ->
                val newPayload = transformer(message.payload)
                val transformed: MagixMessage<R> = MagixMessage(
                    outputFormat,
                    newOrigin ?: message.origin,
                    newPayload,
                    message.target,
                    message.id,
                    message.parentId,
                    message.user
                )
                endpoint.broadcast(outputSerializer, transformed)
            }.launchIn(scope)
        }
    }

    public fun convert(
        scope: CoroutineScope,
        filter: MagixMessageFilter,
        outputFormat: String,
        newOrigin: String? = null,
        transformer: suspend (JsonElement) -> JsonElement,
    ): MagixProcessor = convert(
        scope,
        filter,
        outputFormat,
        JsonElement.serializer(),
        JsonElement.serializer(),
        newOrigin,
        transformer
    )
}
