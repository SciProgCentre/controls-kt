package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem

interface Device {
    val descriptors: Collection<PropertyDescriptor>
    var controller: DeviceController?

    suspend fun getProperty(propertyName: String): MetaItem<*>
    suspend fun setProperty(propertyName: String, propertyValue: MetaItem<*>)

    suspend fun request(command: String, argument: MetaItem<*>?): MetaItem<*>?
}