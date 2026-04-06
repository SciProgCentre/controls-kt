package space.kscience.controls.storage

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.hubMessageFlow
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.context.debug
import space.kscience.dataforge.context.logger
import kotlin.time.Duration

/**
 * Retrieves a [DeviceMessageStorage] instance using the provided factory.
 * The storage is constructed in the context of the current [DeviceManager] instance.
 *
 */
public fun DeviceManager.storage(
    factory: Factory<DeviceMessageStorage>,
): DeviceMessageStorage = factory.build(context, meta)

/**
 * Aggregates elements emitted by the source flow into lists based on a specified time window duration.
 * Each list corresponds to the elements collected during the time window, starting from the first emission.
 *
 */
public fun <T> Flow<T>.timeWindowed(duration: Duration): Flow<List<T>> = channelFlow {
    val mutex = Mutex()
    val collector = ArrayList<T>()
    collect {
        mutex.withLock {
            collector.add(it)
        }
    }
    launch {
        while (isActive) {
            mutex.withLock {
                delay(duration)
                if (collector.isNotEmpty()) {
                    send(collector.toList())
                }
                collector.clear()
            }
        }
    }
}


/**
 * Begin to store DeviceMessages from this DeviceManager
 * @param factory factory that will be used for creating persistent entity store instance. DefaultPersistentStoreFactory by default.
 * DeviceManager's meta and context will be used for in invoke method.
 * @param batchWindow if not null, messages will be grouped into time windows of this duration and batch written to storage.
 * @param filterCondition allow you to specify messages which we want to store. Always true by default.
 * @return Job which responsible for our storage
 */
public fun DeviceManager.storeMessages(
    factory: Factory<DeviceMessageStorage>,
    batchWindow: Duration? = null,
    filterCondition: suspend (DeviceMessage) -> Boolean = { true },
): Job {
    val storage = factory.build(context, meta)
    logger.debug { "Message storage with meta = $meta created" }

    return hubMessageFlow().filter(filterCondition).let {
        //if batch window is specified, group messages into batches and write them to storage
        if (batchWindow != null) {
            it.timeWindowed(batchWindow).onEach { messages ->
                storage.writeAll(messages)
            }
        } else {
            it.onEach { message ->
                storage.write(message)
            }
        }
    }.onCompletion {
        storage.close()
        logger.debug { "Message storage closed" }
    }.launchIn(context)
}


