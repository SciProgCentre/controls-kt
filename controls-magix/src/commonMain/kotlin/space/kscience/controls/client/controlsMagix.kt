package space.kscience.controls.client

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CancellationException
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


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
    uuid4().leastSignificantBits.toULong().toString(16)
}

/**
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1)
 *
 * Accepts messages with target that equals [endpointID] or null (broadcast messages)
 */
public fun DeviceManager.launchMagixService(
    endpoint: MagixEndpoint,
    endpointID: String,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): Job = context.launch(coroutineContext) {
    endpoint.subscribe(controlsMagixFormat, targetFilter = listOf(endpointID, null)).onEach { (request, payload) ->
        val responsePayload: List<DeviceMessage> = respondHubMessage(payload)
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
        if (error !is CancellationException) logger.error(error) { "Error while responding to message: ${error.message}" }
    }.launchIn(this)

    hubMessageFlow().onEach { payload ->
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


