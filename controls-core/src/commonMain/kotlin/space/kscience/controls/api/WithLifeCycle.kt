package space.kscience.controls.api

import kotlinx.serialization.Serializable

/**
 * A lifecycle state of a device
 */
@Serializable
public enum class LifecycleState {

    /**
     * Device is initializing
     */
    STARTING,

    /**
     * The Device is initialized and running
     */
    STARTED,

    /**
     * The Device is closed
     */
    STOPPED,

    /**
     * The device encountered irrecoverable error
     */
    ERROR
}


/**
 * An object that could be started or stopped functioning
 */
public interface WithLifeCycle {

    public suspend fun start()

    public suspend fun stop()

    public val lifecycleState: LifecycleState
}

/**
 * Bind this object lifecycle to a device lifecycle
 *
 * The starting and stopping are done in device scope
 */
public fun WithLifeCycle.bindToDeviceLifecycle(device: Device){
    device.onLifecycleEvent {
        when(it){
            LifecycleState.STARTING -> start()
            LifecycleState.STARTED -> {/*ignore*/}
            LifecycleState.STOPPED -> stop()
            LifecycleState.ERROR -> stop()
        }
    }
}