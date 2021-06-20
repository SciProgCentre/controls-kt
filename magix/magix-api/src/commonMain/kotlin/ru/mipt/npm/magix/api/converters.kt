package space.kscience.dataforge.magix.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import ru.mipt.npm.magix.api.MagixMessageFilter

/**
 * Launch magix message converter service
 */
public fun <T, R> CoroutineScope.launchMagixConverter(
    inputEndpoint: MagixEndpoint<T>,
    outputEndpoint: MagixEndpoint<R>,
    filter: MagixMessageFilter,
    outputFormat: String,
    newOrigin: String? = null,
    transformer: suspend (T) -> R,
): Job = inputEndpoint.subscribe(filter).onEach { message->
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
    outputEndpoint.broadcast(transformed)
}.launchIn(this)
