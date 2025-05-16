package space.kscience.controls.spec.model

/**
 * Enum defining how a child device's lifecycle is coupled to its parent.
 */
public enum class LifecycleMode {
    /** Linked mode - child device starts/stops with parent. */
    LINKED,

    /** Independent mode - child device must be started/stopped manually. */
    INDEPENDENT
}

/**
 * Enum defining how a device should be started when attached to a manager.
 */
public enum class StartMode {
    /** Don't start device automatically. */
    NONE,

    /** Start device asynchronously, not waiting for completion. */
    ASYNC,

    /** Start device synchronously, waiting for startup completion. */
    SYNC
}

/**
 * Enum defining strategies for handling errors encountered in child devices.
 */
public enum class ChildDeviceErrorHandler {
    /** Ignore errors, only log them. The parent device continues operation. */
    IGNORE,

    /** Attempt to restart the failed child device according to its [RestartPolicy]. */
    RESTART,

    /** Stop the parent device if a child device encounters a critical error. */
    STOP_PARENT,

    /** Propagate the error upwards, potentially cancelling the parent's coroutine or operation. */
    PROPAGATE
}