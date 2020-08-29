package hep.dataforge.control.api

import hep.dataforge.control.api.Device.Companion.DEVICE_TARGET
import hep.dataforge.io.Envelope
import hep.dataforge.io.EnvelopeBuilder
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaItem
import hep.dataforge.provider.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.io.Closeable

interface Consumer {
    fun consume(message: Envelope): Unit
}

/**
 *  General interface describing a managed Device
 */
@Type(DEVICE_TARGET)
interface Device : Closeable {
    /**
     * List of supported property descriptors
     */
    val propertyDescriptors: Collection<PropertyDescriptor>

    /**
     * List of supported action descriptors. Action is a request to the device that
     * may or may not change the properties
     */
    val actionDescriptors: Collection<ActionDescriptor>

    /**
     * The scope encompassing all operations on a device. When canceled, cancels all running processes
     */
    val scope: CoroutineScope

    /**
     * Register a new property change listener for this device.
     * [owner] is provided optionally in order for listener to be
     * easily removable
     */
    fun registerListener(listener: DeviceListener, owner: Any? = listener)

    /**
     * Remove all listeners belonging to the specified owner
     */
    fun removeListeners(owner: Any?)

    /**
     * Get the value of the property or throw error if property in not defined.
     * Suspend if property value is not available
     */
    suspend fun getProperty(propertyName: String): MetaItem<*>

    /**
     * Invalidate property and force recalculate
     */
    suspend fun invalidateProperty(propertyName: String)

    /**
     * Set property [value] for a property with name [propertyName].
     * In rare cases could suspend if the [Device] supports command queue and it is full at the moment.
     */
    suspend fun setProperty(propertyName: String, value: MetaItem<*>)

    /**
     * Send an action request and suspend caller while request is being processed.
     * Could return null if request does not return a meaningful answer.
     */
    suspend fun execute(command: String, argument: MetaItem<*>? = null): MetaItem<*>?

    /**
     *
     * A request with binary data or for binary response (or both). This request does not cover basic functionality like
     * [setProperty], [getProperty] or [execute] and not defined for a generic device.
     *
     * TODO implement Responder after DF 0.1.9
     */
    suspend fun respond(request: Envelope): EnvelopeBuilder = error("No binary response defined")

    override fun close() {
        scope.cancel("The device is closed")
    }

    companion object {
        const val DEVICE_TARGET = "device"
    }
}

suspend fun Device.execute(name: String, meta: Meta?): MetaItem<*>? = execute(name, meta?.let { MetaItem.NodeItem(it) })