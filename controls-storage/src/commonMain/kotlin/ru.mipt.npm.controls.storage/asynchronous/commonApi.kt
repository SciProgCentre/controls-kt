package ru.mipt.npm.controls.storage.asynchronous

import io.ktor.utils.io.core.use
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta

/**
 * Asynchronous version of synchronous API, so for more details check relative docs
 */

@OptIn(InternalCoroutinesApi::class)
public fun DeviceManager.storeMessages(
    factory: Factory<AsynchronousStorageClient>,
    filterCondition: suspend (DeviceMessage) -> Boolean = { true },
): Job {
    val client = factory(meta, context)
    logger.debug { "Storage client created" }

    return hubMessageFlow(context).filter(filterCondition).onEach { message ->
        client.storeValueInDeviceHub(message)
    }.launchIn(context).apply {
        invokeOnCompletion(onCancelling = true) {
            client.close()
            logger.debug { "Storage client closed" }
        }
    }
}

public suspend fun getPropertyHistory(
    sourceDeviceName: String,
    propertyName: String,
    factory: Factory<AsynchronousStorageClient>,
    meta: Meta = Meta.EMPTY
): List<PropertyChangedMessage> {
    return factory(meta).use {
        it.getPropertyHistory(sourceDeviceName, propertyName)
    }
}

