package hep.dataforge.control.client

import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.DeviceMessage
import hep.dataforge.control.controllers.respondMessage
import hep.dataforge.magix.api.MagixEndpoint
import hep.dataforge.magix.api.MagixMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


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
    endpoint: MagixEndpoint,
    endpointID: String = DATAFORGE_MAGIX_FORMAT,
): Job = context.launch {
    endpoint.subscribe(DeviceMessage.serializer()).onEach { request ->
        //TODO analyze action

        val responsePayload = respondMessage(request.payload)
        val response = MagixMessage(
            format = DATAFORGE_MAGIX_FORMAT,
            id = generateId(request),
            parentId = request.id,
            origin = endpointID,
            payload = responsePayload
        )
        endpoint.broadcast(DeviceMessage.serializer(), response)
    }.catch { error ->
        logger.error(error){"Error while responding to message"}
    }.launchIn(endpoint.scope)

    controller.messageOutput.onEach { payload ->
        MagixMessage(
            format = DATAFORGE_MAGIX_FORMAT,
            id = "df[${payload.hashCode()}]",
            origin = endpointID,
            payload = payload
        )
    }.catch { error ->
        logger.error(error){"Error while sending a message"}
    }.launchIn(endpoint.scope)
}

public fun DeviceManager.asMagixProcessor(endpointID: String = "dataforge"): MagixProcessor = object : MagixProcessor {
    override fun process(endpoint: MagixEndpoint): Job = launchMagixClient(endpoint, endpointID)

}


