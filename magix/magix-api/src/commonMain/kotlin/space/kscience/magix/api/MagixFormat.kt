package space.kscience.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import space.kscience.magix.api.MagixEndpoint.Companion.magixJson

/**
 * A format for [MagixMessage] that allows to decode typed payload
 *
 * @param formats allowed values of the format field that are processed. The first value is the primary format for sending.
 */
public data class MagixFormat<T>(
    val serializer: KSerializer<T>,
    val formats: Set<String>,
) {
    val defaultFormat: String get() = formats.firstOrNull() ?: "magix"
}

/**
 * Subscribe for messages in given endpoint using [format] to declare format filter as well as automatic decoding.
 *
 * @return a flow of pairs (raw message, decoded message). Raw messages are to be used to extract headers.
 */
public fun <T> MagixEndpoint.subscribe(
    format: MagixFormat<T>,
    originFilter: Collection<String>? = null,
    targetFilter: Collection<String?>? = null,
): Flow<Pair<MagixMessage, T>> = subscribe(
    MagixMessageFilter(format = format.formats, source = originFilter, target = targetFilter)
).map {
    val value: T = magixJson.decodeFromJsonElement(format.serializer, it.payload)
    it to value
}

/**
 * Send a message using given [format] to encode the message payload. The format field is also taken from [format].
 *
 */
public suspend fun <T> MagixEndpoint.send(
    format: MagixFormat<T>,
    payload: T,
    source: String,
    target: String? = null,
    id: String? = null,
    parentId: String? = null,
    user: JsonElement? = null,
) {
    val message = MagixMessage(
        format = format.defaultFormat,
        payload = magixJson.encodeToJsonElement(format.serializer, payload),
        sourceEndpoint = source,
        targetEndpoint = target,
        id = id,
        parentId = parentId,
        user = user
    )
    send(message)
}

