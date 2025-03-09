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


public fun DeviceManager.storage(
    factory: Factory<DeviceMessageStorage>,
): DeviceMessageStorage = factory.build(context, meta)

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
    val storage = factory.build(context, meta)
    logger.debug { "Message storage with meta = $meta created" }

    return hubMessageFlow().filter(filterCondition).onEach { message ->
        storage.write(message)
    }.onCompletion {
        storage.close()
        logger.debug { "Message storage closed" }
    }.launchIn(context)
}


