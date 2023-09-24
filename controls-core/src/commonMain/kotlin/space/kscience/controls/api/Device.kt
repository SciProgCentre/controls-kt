package space.kscience.controls.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import space.kscience.controls.api.Device.Companion.DEVICE_TARGET
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.misc.DFExperimental
import space.kscience.dataforge.misc.Type
import space.kscience.dataforge.names.Name

/**
 * A lifecycle state of a device
 */
public enum class DeviceLifecycleState{
    INIT,
    OPEN,
    CLOSED
}

/**
 *  General interface describing a managed Device.
 *  [Device] is a supervisor scope encompassing all operations on a device.
 *  When canceled, cancels all running processes.
 */
@Type(DEVICE_TARGET)
public interface Device : AutoCloseable, ContextAware, CoroutineScope {

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
     * Get the logical state of property or return null if it is invalid
     */
    public fun getProperty(propertyName: String): Meta?

    /**
     * Invalidate property (set logical state to invalid)
     *
     * This message is suspended to provide lock-free local property changes (they require coroutine context).
     */
    public suspend fun invalidate(propertyName: String)

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
     * Initialize the device. This function suspends until the device is finished initialization
     */
    public suspend fun open(): Unit = Unit

    /**
     * Close and terminate the device. This function does not wait for the device to be closed.
     */
    override fun close() {
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
 * Get the logical state of property or suspend to read the physical value.
 */
public suspend fun Device.getOrReadProperty(propertyName: String): Meta =
    getProperty(propertyName) ?: readProperty(propertyName)

/**
 * Get a snapshot of the device logical state
 *
 */
public fun Device.getAllProperties(): Meta = Meta {
    for (descriptor in propertyDescriptors) {
        setMeta(Name.parse(descriptor.name), getProperty(descriptor.name))
    }
}

/**
 * Subscribe on property changes for the whole device
 */
public fun Device.onPropertyChange(scope: CoroutineScope = this, callback: suspend PropertyChangedMessage.() -> Unit): Job =
    messageFlow.filterIsInstance<PropertyChangedMessage>().onEach(callback).launchIn(scope)
