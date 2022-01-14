package ru.mipt.npm.controls.storage.synchronous

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.io.core.use
import ru.mipt.npm.controls.api.DeviceMessage
import ru.mipt.npm.controls.api.PropertyChangedMessage
import ru.mipt.npm.controls.controllers.DeviceManager
import ru.mipt.npm.controls.controllers.hubMessageFlow
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta

public enum class StorageKind {
    DEVICE_HUB,
    MAGIX_SERVER
}

/**
 * Begin to store DeviceMessages from this DeviceManager
 * @param factory factory that will be used for creating persistent entity store instance. DefaultPersistentStoreFactory by default.
 * DeviceManager's meta and context will be used for in invoke method.
 * @param filterCondition allow you to specify messages which we want to store. Always true by default.
 * @return Job which responsible for our storage
 */
@OptIn(InternalCoroutinesApi::class)
public fun DeviceManager.storeMessages(
    factory: Factory<SynchronousStorageClient>,
    filterCondition: suspend (DeviceMessage) -> Boolean = { true },
): Job {
    val client = factory(meta, context)
    logger.debug { "Storage client created" }

    return hubMessageFlow(context).filter(filterCondition).onEach { message ->
        client.storeValue(message, StorageKind.DEVICE_HUB)
    }.launchIn(context).apply {
        invokeOnCompletion(onCancelling = true) {
            client.close()
            logger.debug { "Storage client closed" }
        }
    }
}

/**
 * @return the list of deviceMessages that describes changes of specified property of specified device sorted by time
 * @param sourceDeviceName a name of device, history of which property we want to get
 * @param propertyName a name of property, history of which we want to get
 * @param factory a factory that produce mongo clients
 */
public fun getPropertyHistory(
    sourceDeviceName: String,
    propertyName: String,
    factory: Factory<SynchronousStorageClient>,
    meta: Meta = Meta.EMPTY
): List<PropertyChangedMessage> {
    return factory(meta).use {
        it.getPropertyHistory(sourceDeviceName, propertyName)
    }
}
