package ru.mipt.npm.controls.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.manager.DeviceManager
import ru.mipt.npm.controls.manager.hubMessageFlow
import ru.mipt.npm.controls.manager.respondHubMessage
import ru.mipt.npm.magix.api.*
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger


public val controlsMagixFormat: MagixFormat<DeviceMessage> = MagixFormat(
    DeviceMessage.serializer(),
    setOf("controls-kt", "dataforge")
)

internal fun generateId(request: MagixMessage): String = if (request.id != null) {
    "${request.id}.response"
} else {
    "df[${request.payload.hashCode()}"
}

/**
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1)
 */
public fun DeviceManager.connectToMagix(
    endpoint: MagixEndpoint,
    endpointID: String = controlsMagixFormat.defaultFormat,
): Job = context.launch {
    endpoint.subscribe(controlsMagixFormat).onEach { (request, payload) ->
        val responsePayload = respondHubMessage(payload)
        if (responsePayload != null) {
            endpoint.broadcast(
                format = controlsMagixFormat,
                origin = endpointID,
                payload = responsePayload,
                id = generateId(request),
                parentId = request.id
            )
        }
    }.catch { error ->
        logger.error(error) { "Error while responding to message" }
    }.launchIn(this)

    hubMessageFlow(this).onEach { payload ->
        endpoint.broadcast(
            format = controlsMagixFormat,
            origin = endpointID,
            payload = payload,
            id = "df[${payload.hashCode()}]"
        )
    }.catch { error ->
        logger.error(error) { "Error while sending a message" }
    }.launchIn(this)
}


