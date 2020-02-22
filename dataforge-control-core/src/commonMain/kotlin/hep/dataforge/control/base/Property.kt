package hep.dataforge.control.base

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.MetaItem

/**
 * Read-only device property
 */
interface ReadOnlyProperty {
    /**
     * Property name, should be unique in device
     */
    val name: String

    val owner: Device

    /**
     * Property descriptor
     */
    val descriptor: PropertyDescriptor

    /**
     *  Get cached value and return null if value is invalid
     */
    fun peek(): MetaItem<*>?

    /**
     * Read value either from cache if cache is valid or directly from physical device
     */
    suspend fun read(): MetaItem<*>
}

/**
 *  A single writeable property handler
 */
interface Property : ReadOnlyProperty {

    /**
     * Update property logical value and notify listener without writing it to device
     */
    suspend fun update(item: MetaItem<*>)

    /**
     * Erase logical value and force re-read from device on next [read]
     */
    suspend fun invalidate()

    /**
     * Write value to physical device. Invalidates logical value, but does not update it automatically
     */
    suspend fun write(item: MetaItem<*>)
}

