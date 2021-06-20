package space.kscience.dataforge.control.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.mipt.npm.magix.api.MagixEndpoint
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.control.controllers.DeviceManager
import space.kscience.dataforge.control.controllers.respondMessage
import space.kscience.dataforge.control.messages.DeviceMessage
import space.kscience.dataforge.magix.api.MagixMessage


public const val DATAFORGE_MAGIX_FORMAT: String = "dataforge"

private fun generateId(request: MagixMessage<DeviceMessage>): String = if (request.id != null) {
    "${request.id}.response"
} else {
    "df[${request.payload.hashCode()}"
}

/**
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1)
 */
public fun DeviceManager.launchMagixClient(
    endpoint: MagixEndpoint<DeviceMessage>,
    endpointID: String = DATAFORGE_MAGIX_FORMAT,
): Job = context.launch {
    endpoint.subscribe().onEach { request ->
        //TODO analyze action

        val responsePayload = respondMessage(request.payload)
        val response = MagixMessage(
            format = DATAFORGE_MAGIX_FORMAT,
            id = generateId(request),
            parentId = request.id,
            origin = endpointID,
            payload = responsePayload
        )
        endpoint.broadcast(response)
    }.catch { error ->
        logger.error(error) { "Error while responding to message" }
    }.launchIn(this)

    controller.messageOutput().onEach { payload ->
        MagixMessage(
            format = DATAFORGE_MAGIX_FORMAT,
            id = "df[${payload.hashCode()}]",
            origin = endpointID,
            payload = payload
        )
    }.catch { error ->
        logger.error(error) { "Error while sending a message" }
    }.launchIn(this)
}


