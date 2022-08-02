package space.kscience.controls.storage

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.hubMessageFlow
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger

//TODO replace by plugin?
public fun DeviceManager.storage(
    factory: Factory<DeviceMessageStorage>,
): DeviceMessageStorage = factory(meta, context)

/**
 * Begin to store DeviceMessages from this DeviceManager
 * @param factory factory that will be used for creating persistent entity store instance. DefaultPersistentStoreFactory by default.
 * DeviceManager's meta and context will be used for in invoke method.
 * @param filterCondition allow you to specify messages which we want to store. Always true by default.
 * @return Job which responsible for our storage
 */
public fun DeviceManager.storeMessages(
    factory: Factory<DeviceMessageStorage>,
    filterCondition: suspend (DeviceMessage) -> Boolean = { true },
): Job {
    val storage = factory(meta, context)
    logger.debug { "Message storage with meta = $meta created" }

    return hubMessageFlow(context).filter(filterCondition).onEach { message ->
        storage.write(message)
    }.onCompletion {
        storage.close()
        logger.debug { "Message storage closed" }
    }.launchIn(context)
}

///**
// * @return the list of deviceMessages that describes changes of specified property of specified device sorted by time
// * @param sourceDeviceName a name of device, history of which property we want to get
// * @param propertyName a name of property, history of which we want to get
// * @param factory a factory that produce mongo clients
// */
//public suspend fun getPropertyHistory(
//    sourceDeviceName: String,
//    propertyName: String,
//    factory: Factory<EventStorage>,
//    meta: Meta = Meta.EMPTY,
//): List<PropertyChangedMessage> {
//    return factory(meta).use {
//        it.getPropertyHistory(sourceDeviceName, propertyName)
//    }
//}
//
//
//public enum class StorageKind {
//    DEVICE_HUB,
//    MAGIX_SERVER
//}

