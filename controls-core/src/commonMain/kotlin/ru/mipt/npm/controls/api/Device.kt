package ru.mipt.npm.controls.api

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import ru.mipt.npm.controls.api.Device.Companion.DEVICE_TARGET
import space.kscience.dataforge.context.ContextAware
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.misc.Type
import space.kscience.dataforge.names.Name


/**
 *  General interface describing a managed Device.
 *  Device is a supervisor scope encompassing all operations on a device. When canceled, cancels all running processes.
 */
@Type(DEVICE_TARGET)
public interface Device : Closeable, ContextAware, CoroutineScope {
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
     * Read physical state of property and update/push notifications if needed.
     */
    public suspend fun readProperty(propertyName: String): Meta

    /**
     * Get the logical state of property or return null if it is invalid
     */
    public fun getProperty(propertyName: String): Meta?

    /**
     * Invalidate property (set logical state to invalid)
     */
    public suspend fun invalidate(propertyName: String)

    /**
     * Set property [value] for a property with name [propertyName].
     * In rare cases could suspend if the [Device] supports command queue and it is full at the moment.
     */
    public suspend fun writeProperty(propertyName: String, value: Meta)

    /**
     * A subscription-based [Flow] of [DeviceMessage] provided by device. The flow is guaranteed to be readable
     * multiple times
     */
    public val messageFlow: Flow<DeviceMessage>

    /**
     * Send an action request and suspend caller while request is being processed.
     * Could return null if request does not return a meaningful answer.
     */
    public suspend fun execute(action: String, argument: Meta? = null): Meta?

    override fun close() {
        cancel("The device is closed")
    }

    public companion object {
        public const val DEVICE_TARGET: String = "device"
    }
}


/**
 * Get the logical state of property or suspend to read the physical value.
 */
public suspend fun Device.getOrReadItem(propertyName: String): Meta =
    getProperty(propertyName) ?: readProperty(propertyName)

/**
 * Get a snapshot of logical state of the device
 */
public fun Device.getProperties(): Meta = Meta {
    for (descriptor in propertyDescriptors) {
        setMeta(Name.parse(descriptor.name), getProperty(descriptor.name))
    }
}

//public suspend fun Device.execute(name: String, meta: Meta?): Meta? = execute(name, meta?.let { MetaNode(it) })