package hep.dataforge.control.api

import hep.dataforge.meta.Meta
import hep.dataforge.meta.MetaItem
import kotlinx.coroutines.CoroutineScope

interface Device {
    /**
     * List of supported property descriptors
     */
    val propertyDescriptors: Collection<PropertyDescriptor>

    /**
     * List of supported requests descriptors
     */
    val actionDescriptors: Collection<ActionDescriptor>

    /**
     * The scope encompassing all operations on a device. When canceled, cancels all running processes
     */
    val scope: CoroutineScope

    var listener: PropertyChangeListener?

    /**
     * Get the value of the property or throw error if property in not defined. Suspend if property value is not available
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
     * Send a request and suspend caller while request is being processed.
     * Could return null if request does not return meaningful answer.
     */
    suspend fun action(name: String, argument: Meta? = null): Meta?

    companion object {
        const val GET_PROPERTY_ACTION = "@getProperty"
        const val SET_PROPERTY_ACTION = "@setProperty"
    }
}