package space.kscience.controls.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import space.kscience.controls.api.Device.Companion.DEVICE_TARGET
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.misc.DfType
import space.kscience.dataforge.names.parseAsName

/**
 * A lifecycle state of a device
 */
@Serializable
public enum class DeviceLifecycleState {

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
 *  General interface describing a managed Device.
 *  [Device] is a supervisor scope encompassing all operations on a device.
 *  When canceled, cancels all running processes.
 */
@DfType(DEVICE_TARGET)
public interface Device : ContextAware, CoroutineScope {

    /**
     * Initial configuration meta for the device
     */
    public val meta: Meta get() = Meta.EMPTY


    /**
     * List of supported property descriptors
     */
    public val propertyDescriptors: Collection<PropertyDescriptor>

    /**
     * List of supported action descriptors. Action is a request to the device that
     * may or may not change the properties
     */
    public val actionDescriptors: Collection<ActionDescriptor>

    /**
     * Read the physical state of property and update/push notifications if needed.
     */
    public suspend fun readProperty(propertyName: String): Meta

    /**
     * Set property [value] for a property with name [propertyName].
     * In rare cases could suspend if the [Device] supports command queue, and it is full at the moment.
     */
    public suspend fun writeProperty(propertyName: String, value: Meta)

    /**
     * A subscription-based [Flow] of [DeviceMessage] provided by device. The flow is guaranteed to be readable
     * multiple times.
     */
    public val messageFlow: Flow<DeviceMessage>

    /**
     * Send an action request and suspend caller while request is being processed.
     * Could return null if request does not return a meaningful answer.
     */
    public suspend fun execute(actionName: String, argument: Meta? = null): Meta?

    /**
     * Initialize the device. This function suspends until the device is finished initialization.
     * Does nothing if the device is started or is starting
     */
    public suspend fun start(): Unit = Unit

    /**
     * Close and terminate the device. This function does not wait for the device to be closed.
     */
    public fun stop() {
        logger.info { "Device $this is closed" }
        cancel("The device is closed")
    }

    @DFExperimental
    public val lifecycleState: DeviceLifecycleState

    public companion object {
        public const val DEVICE_TARGET: String = "device"
    }
}

/**
 * Inner id of a device. Not necessary corresponds to the name in the parent container
 */
public val Device.id: String get() = meta["id"].string?: "device[${hashCode().toString(16)}]"

/**
 * Device that caches properties values
 */
public interface CachingDevice : Device {

    /**
     * Immediately (without waiting) get the cached (logical) state of property or return null if it is invalid
     */
    public fun getProperty(propertyName: String): Meta?

    /**
     * Invalidate property (set logical state to invalid).
     *
     * This message is suspended to provide lock-free local property changes (they require coroutine context).
     */
    public suspend fun invalidate(propertyName: String)
}

/**
 * Get the logical state of property or suspend to read the physical value.
 */
public suspend fun Device.getOrReadProperty(propertyName: String): Meta = if (this is CachingDevice) {
    getProperty(propertyName) ?: readProperty(propertyName)
} else {
    readProperty(propertyName)
}

/**
 * Get a snapshot of the device logical state
 *
 */
public fun CachingDevice.getAllProperties(): Meta = Meta {
    for (descriptor in propertyDescriptors) {
        set(descriptor.name.parseAsName(), getProperty(descriptor.name))
    }
}

/**
 * Subscribe on property changes for the whole device
 */
public fun Device.onPropertyChange(
    scope: CoroutineScope = this,
    callback: suspend PropertyChangedMessage.() -> Unit,
): Job = messageFlow.filterIsInstance<PropertyChangedMessage>().onEach(callback).launchIn(scope)

/**
 * A [Flow] of property change messages for specific property.
 */
public fun Device.propertyMessageFlow(propertyName: String): Flow<PropertyChangedMessage> = messageFlow
    .filterIsInstance<PropertyChangedMessage>()
    .filter { it.property == propertyName }
