package ru.mipt.npm.controls.client

import jetbrains.exodus.entitystore.PersistentEntityStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow
import ru.mipt.npm.controls.controllers.respondHubMessage
import ru.mipt.npm.controls.xodus.toEntity
import ru.mipt.npm.magix.api.MagixEndpoint
import ru.mipt.npm.magix.api.MagixMessage
import space.kscience.dataforge.context.error
import space.kscience.dataforge.context.logger

/**
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1) and dump messages at xodus entity store
 */
public fun DeviceManager.connectToMagix(
    endpoint: MagixEndpoint<DeviceMessage>,
    endpointID: String = DATAFORGE_MAGIX_FORMAT,
    entityStore: PersistentEntityStore
): Job = context.launch {
    endpoint.subscribe().onEach { request ->
        val responsePayload = respondHubMessage(request.payload)
        if (responsePayload != null) {
            val response = MagixMessage(
                format = DATAFORGE_MAGIX_FORMAT,
                id = generateId(request),
                parentId = request.id,
                origin = endpointID,
                payload = responsePayload
            )

            endpoint.broadcast(response)
        }
    }.catch { error ->
        logger.error(error) { "Error while responding to message" }
    }.launchIn(this)

    hubMessageFlow(this).onEach { payload ->
        val magixMessage = MagixMessage(
            format = DATAFORGE_MAGIX_FORMAT,
            id = "df[${payload.hashCode()}]",
            origin = endpointID,
            payload = payload
        )
        endpoint.broadcast(magixMessage)
        if (payload is PropertyChangedMessage) {
            entityStore.executeInTransaction {
                magixMessage.toEntity(it)
            }
        }
    }.catch { error ->
        logger.error(error) { "Error while sending a message" }
    }.launchIn(this)
}
