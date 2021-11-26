package ru.mipt.npm.controls.xodus

import jetbrains.exodus.entitystore.PersistentEntityStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow


public fun DeviceManager.connectXodus(
    entityStore: PersistentEntityStore,
    filterCondition: suspend (DeviceMessage) -> Boolean  = { it is PropertyChangedMessage }
): Job = hubMessageFlow(context).filter(filterCondition).onEach { message ->
    entityStore.executeInTransaction {
        (message as PropertyChangedMessage).toEntity(it)
    }
}.launchIn(context)