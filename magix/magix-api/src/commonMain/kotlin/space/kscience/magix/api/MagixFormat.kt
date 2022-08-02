package space.kscience.magix.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import space.kscience.magix.api.MagixEndpoint.Companion.magixJson

public data class MagixFormat<T>(
    val serializer: KSerializer<T>,
    val formats: Set<String>,
) {
    val defaultFormat: String get() = formats.firstOrNull() ?: "magix"
}

public fun <T> MagixEndpoint.subscribe(
    format: MagixFormat<T>,
    originFilter: Collection<String?>? = null,
    targetFilter: Collection<String?>? = null,
): Flow<Pair<MagixMessage, T>> = subscribe(
    MagixMessageFilter(format = format.formats, origin = originFilter, target = targetFilter)
).map {
    val value: T = magixJson.decodeFromJsonElement(format.serializer, it.payload)
    it to value
}

public suspend fun <T> MagixEndpoint.broadcast(
    format: MagixFormat<T>,
    payload: T,
    target: String? = null,
    id: String? = null,
    parentId: String? = null,
    user: JsonElement? = null,
    origin: String = format.defaultFormat,
) {
    val message = MagixMessage(
        format = format.defaultFormat,
        payload = magixJson.encodeToJsonElement(format.serializer, payload),
        origin = origin,
        target = target,
        id = id,
        parentId = parentId,
        user = user
    )
    broadcast(message)
}

