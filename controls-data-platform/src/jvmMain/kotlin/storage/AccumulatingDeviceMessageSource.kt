package space.kscience.controls.dataplatform.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.kscience.controls.api.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name

/**
 *  A wrapper for [DeviceMessageSource] that accumulates current devices state.
 */
public class AccumulatingDeviceMessageSource(
    public val scope: CoroutineScope,
    public val source: DeviceMessageSource,
) : DeviceMessageSource {

    private val mutex = Mutex()

    private val propertyValues = mutableMapOf<Name, MutableMap<String, Meta?>>()

    private val lifecycleState = mutableMapOf<Name, LifecycleState>()

    private suspend fun processMessage(message: DeviceMessage) = mutex.withLock{
        when(message){
            is PropertyChangedMessage -> {
                propertyValues.getOrPut(message.sourceDevice) { mutableMapOf() }.set(message.property, message.value)
            }
            is DeviceLifeCycleMessage -> lifecycleState[message.sourceDevice] = message.state
            else -> {
                // do nothing
            }
        }
    }

    override val messageFlow: SharedFlow<DeviceMessage> = source.messageFlow.onEach {
        processMessage(it)
    }.shareIn(scope, SharingStarted.Eagerly)


    public suspend fun readProperty(device: Name, property: String): Meta? = mutex.withLock {
        propertyValues[device]?.get(property)
    }

    public suspend fun readLifecycleState(device: Name): LifecycleState? = mutex.withLock {
        lifecycleState[device]
    }

}


public fun DeviceMessageSource.accumulateMessages(scope: CoroutineScope): AccumulatingDeviceMessageSource =
    this as? AccumulatingDeviceMessageSource ?: AccumulatingDeviceMessageSource(scope = scope, source = this)