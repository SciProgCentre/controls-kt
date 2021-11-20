package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.PersistentEntityStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow


public fun DeviceManager.connectXodus(
    entityStore: PersistentEntityStore,
    //filter: (DeviceMessage) -> Boolean  = {it is PropertyChangedMessage}
): Job = hubMessageFlow(context).onEach { message ->
    if (message is PropertyChangedMessage) {
        entityStore.executeInTransaction {
            message.toEntity(it)
        }
    }
}.launchIn(context)