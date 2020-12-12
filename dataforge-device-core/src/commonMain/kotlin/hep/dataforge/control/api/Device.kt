package hep.dataforge.control.api

import hep.dataforge.context.ContextAware
import hep.dataforge.control.api.Device.Companion.DEVICE_TARGET
import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaItem
import hep.dataforge.provider.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.io.Closeable

/**
 *  General interface describing a managed Device
 */
@Type(DEVICE_TARGET)
public interface Device : Closeable, ContextAware {
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
     * The supervisor scope encompassing all operations on a device. When canceled, cancels all running processes.
     */
    public val scope: CoroutineScope

    /**
     * Get the value of the property or throw error if property in not defined.
     * Suspend if property value is not available
     */
    public suspend fun getProperty(propertyName: String): MetaItem<*>?

    /**
     * Invalidate property and force recalculate
     */
    public suspend fun invalidateProperty(propertyName: String)

    /**
     * Set property [value] for a property with name [propertyName].
     * In rare cases could suspend if the [Device] supports command queue and it is full at the moment.
     */
    public suspend fun setProperty(propertyName: String, value: MetaItem<*>)

    /**
     * The [SharedFlow] of property changes
     */
    public val propertyFlow: SharedFlow<Pair<String, MetaItem<*>>>

    /**
     * Send an action request and suspend caller while request is being processed.
     * Could return null if request does not return a meaningful answer.
     */
    public suspend fun execute(action: String, argument: MetaItem<*>? = null): MetaItem<*>?

    override fun close() {
        scope.cancel("The device is closed")
    }

    public companion object {
        public const val DEVICE_TARGET: String = "device"
    }
}

public suspend fun Device.getState(): Meta = Meta{
    for(descriptor in propertyDescriptors) {
        descriptor.name put getProperty(descriptor.name)
    }
}

//public suspend fun Device.execute(name: String, meta: Meta?): MetaItem<*>? = execute(name, meta?.let { MetaItem.NodeItem(it) })