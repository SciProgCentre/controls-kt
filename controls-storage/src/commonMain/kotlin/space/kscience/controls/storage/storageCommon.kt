package space.kscience.controls.storage

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.DeviceMessage
import space.kscience.controls.api.deviceMessageFlow
import space.kscience.controls.manager.DeviceManager
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
 * Groups elements into non-empty windows starting with the first collected element.
 * Normal completion emits the remaining elements; failure or cancellation does not flush them.
 */
public fun <T> Flow<T>.timeWindowed(duration: Duration): Flow<List<T>> {
    require(duration.isPositive()) { "Window duration must be positive" }
    return channelFlow {
        val mutex = Mutex()
        val collector = ArrayList<T>()
        var timerJob: Job? = null
        suspend fun flush() {
            mutex.withLock {
                if (collector.isNotEmpty()) {
                    send(collector.toList())
                    collector.clear()
                }
            }
        }

        collect { item ->
            mutex.withLock {
                val first = collector.isEmpty()
                collector.add(item)
                if (first) {
                    timerJob = launch(start = CoroutineStart.UNDISPATCHED) {
                        delay(duration)
                        flush()
                    }
                }
            }
        }
        flush()
        timerJob?.cancelAndJoin()
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

    return deviceMessageFlow().filter(filterCondition).let {
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


