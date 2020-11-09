package hep.dataforge.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

public interface MagixProcessor {
    public fun process(endpoint: MagixEndpoint): Job
}

/**
 * A converter from one (or several) format to another. It captures all events with the given filter then transforms it
 * with given [transformer] and sends back to the loop with given [outputFormat].
 *
 * If [newOrigin] is not null, it is used as a replacement for old [MagixMessage.origin] tag.
 */
public class MagixConverter(
    private val scope: CoroutineScope,
    private val filter: MagixMessageFilter,
    private val outputFormat: String,
    private val newOrigin: String? = null,
    private val transformer: suspend (JsonElement) -> JsonElement,
) : MagixProcessor {
    override fun process(endpoint: MagixEndpoint): Job = scope.launch {
        endpoint.subscribe(filter).onEach { message ->
            val newPayload = transformer(message.payload)
            val transformed = message.copy(
                payload = newPayload,
                format = outputFormat,
                origin = newOrigin ?: message.origin
            )
            endpoint.broadcast(transformed)
        }.launchIn(this)
        //TODO add catch logic here
    }
}