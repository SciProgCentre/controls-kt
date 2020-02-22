package hep.dataforge.control.api

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
    val requestDescriptors: Collection<RequestDescriptor>

    /**
     * The scope encompassing all operations on a device. When canceled, cancels all running processes
     */
    val scope: CoroutineScope

    var controller: PropertyChangeListener?

    /**
     * Get the value of the property or throw error if property in not defined. Suspend if property value is not available
     */
    suspend fun getProperty(propertyName: String): MetaItem<*>

    /**
     * Set property [value] for a property with name [propertyName].
     * In rare cases could suspend if the [Device] supports command queue and it is full at the moment.
     */
    suspend fun setProperty(propertyName: String, value: MetaItem<*>)

    /**
     * Send a request and suspend caller while request is being processed.
     * Could return null if request does not return meaningful answer.
     */
    suspend fun request(name: String, argument: MetaItem<*>? = null): MetaItem<*>?
}