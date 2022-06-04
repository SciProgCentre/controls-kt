package ru.mipt.npm.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement

/**
 * Launch magix message converter service
 */
public fun <T, R> CoroutineScope.launchMagixConverter(
    endpoint: MagixEndpoint,
    filter: MagixMessageFilter,
    outputFormat: String,
    newOrigin: String? = null,
    transformer: suspend (JsonElement) -> JsonElement,
): Job = endpoint.subscribe(filter).onEach { message->
    val newPayload = transformer(message.payload)
    val transformed: MagixMessage = MagixMessage(
        newOrigin ?: message.origin,
        newPayload,
        outputFormat,
        message.target,
        message.id,
        message.parentId,
        message.user
    )
    endpoint.broadcast(transformed)
}.launchIn(this)
