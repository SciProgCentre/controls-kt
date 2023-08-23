package space.kscience.magix.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter

/**
 * Launch magix message converter service.
 *
 * The service converts a payload from one format into another.
 *
 * @param endpoint The endpoint this converter is attached to
 * @param filter a filter for messages to be converted.
 * @param outputFormat a new value for [MagixMessage.format] field
 * @param newSource a new value of [MagixMessage.sourceEndpoint]. By default uses the original message value
 * @param transformer a function to transform the payload.
 */
public fun CoroutineScope.launchMagixConverter(
    endpoint: MagixEndpoint,
    filter: MagixMessageFilter,
    outputFormat: String,
    newSource: String? = null,
    transformer: suspend (JsonElement) -> JsonElement,
): Job = endpoint.subscribe(filter).onEach { message->
    val newPayload = transformer(message.payload)
    val transformed: MagixMessage = MagixMessage(
        outputFormat,
        newPayload,
        newSource ?: message.sourceEndpoint,
        message.targetEndpoint,
        message.id,
        message.parentId,
        message.user
    )
    endpoint.broadcast(transformed)
}.launchIn(this)
