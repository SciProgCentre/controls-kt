package hep.dataforge.control.client

import hep.dataforge.control.controllers.DeviceManager
import hep.dataforge.control.controllers.respondMessage
import hep.dataforge.control.messages.DeviceMessage
import hep.dataforge.magix.api.MagixEndpoint
import hep.dataforge.magix.api.MagixMessage
import hep.dataforge.magix.api.MagixProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


public const val DATAFORGE_FORMAT: String = "dataforge"

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
    endpointID: String = "dataforge",
): Job = context.launch {
    endpoint.subscribe(DeviceMessage.serializer()).onEach { request ->
        //TODO analyze action

        val responsePayload = respondMessage(request.payload)
        val response = MagixMessage(
            format = DATAFORGE_FORMAT,
            id = generateId(request),
            parentId = request.id,
            origin = endpointID,
            payload = responsePayload
        )
        endpoint.broadcast(DeviceMessage.serializer(), response)
    }.launchIn(this)

    controller.messageOutput().onEach { payload ->
        MagixMessage(
            format = DATAFORGE_FORMAT,
            id = "df[${payload.hashCode()}]",
            origin = endpointID,
            payload = payload
        )
    }.launchIn(this)
}

public fun DeviceManager.asMagixProcessor(endpointID: String = "dataforge"): MagixProcessor = object : MagixProcessor {
    override fun process(endpoint: MagixEndpoint): Job = launchMagixClient(endpoint, endpointID)

}


