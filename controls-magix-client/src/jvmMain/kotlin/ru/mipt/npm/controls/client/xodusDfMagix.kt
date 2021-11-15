package ru.mipt.npm.controls.client

import jetbrains.exodus.entitystore.PersistentEntityStore
import kotlinx.coroutines.Job
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.xodus.toEntity
import ru.mipt.npm.magix.api.MagixEndpoint

/**
 * Communicate with server in [Magix format](https://github.com/waltz-controls/rfc/tree/master/1) and dump messages at xodus entity store
 */
public fun DeviceManager.connectToMagix(
    endpoint: MagixEndpoint<DeviceMessage>,
    endpointID: String = DATAFORGE_MAGIX_FORMAT,
    entityStore: PersistentEntityStore
): Job = connectToMagix(endpoint, endpointID) { message ->
    if (message.payload is PropertyChangedMessage) {
        entityStore.executeInTransaction {
            message.toEntity(it)
        }
    }
}
