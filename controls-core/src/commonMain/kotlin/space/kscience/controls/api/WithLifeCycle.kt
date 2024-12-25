package space.kscience.controls.api

import kotlinx.serialization.Serializable

/**
 * A lifecycle state of a device
 */
@Serializable
public enum class LifecycleState {

    /**
     * The device is newly created and has not started yet.
     */
    INITIAL,

    /**
     * Device is initializing
     */
    STARTING,

    /**
     * The Device is initialized and running
     */
    STARTED,

    /**
     * The Device is stopping
     */
    STOPPING,

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
            LifecycleState.INITIAL -> {/*ignore*/}
            LifecycleState.STARTING -> start()
            LifecycleState.STARTED -> {/*ignore*/}
            LifecycleState.STOPPING -> stop()
            LifecycleState.STOPPED -> stop()
            LifecycleState.ERROR -> stop()
        }
    }
}