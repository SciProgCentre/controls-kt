package space.kscience.controls.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.hubMessageFlow
import space.kscience.controls.manager.respondHubMessage
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger
import space.kscience.magix.api.*


internal val controlsMagixFormat: MagixFormat<DeviceMessage> = MagixFormat(
    DeviceMessage.serializer(),
    setOf("controls-kt")
)

/**
 * A magix message format to work with Controls-kt data
 */
public val DeviceManager.Companion.magixFormat: MagixFormat<DeviceMessage> get() = controlsMagixFormat

internal fun generateId(request: MagixMessage): String = if (request.id != null) {
    "${request.id}.response"
} else {
    "controls[${request.payload.hashCode().toString(16)}"
}

/**
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1)
 */
public fun DeviceManager.launchMagixService(
    endpoint: MagixEndpoint,
    endpointID: String = controlsMagixFormat.defaultFormat,
): Job = context.launch {
    endpoint.subscribe(controlsMagixFormat, targetFilter = listOf(endpointID)).onEach { (request, payload) ->
        val responsePayload = respondHubMessage(payload)
        responsePayload.forEach {
            endpoint.send(
                format = controlsMagixFormat,
                payload = it,
                source = endpointID,
                target = request.sourceEndpoint,
                id = generateId(request),
                parentId = request.id
            )
        }
    }.catch { error ->
        logger.error(error) { "Error while responding to message: ${error.message}" }
    }.launchIn(this)

    hubMessageFlow(this).onEach { payload ->
        endpoint.send(
            format = controlsMagixFormat,
            payload = payload,
            source = endpointID,
            id = "df[${payload.hashCode()}]"
        )
    }.catch { error ->
        logger.error(error) { "Error while sending a message: ${error.message}" }
    }.launchIn(this)
}


