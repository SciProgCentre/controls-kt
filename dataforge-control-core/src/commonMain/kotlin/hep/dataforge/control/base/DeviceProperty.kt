package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.MetaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * Read-only device property
 */
interface ReadOnlyDeviceProperty : ReadOnlyProperty<Any?, MetaItem<*>?> {
    /**
     * Property name, should be unique in device
     */
    val name: String

    /**
     * Property descriptor
     */
    val descriptor: PropertyDescriptor

    val scope: CoroutineScope

    /**
     * Erase logical value and force re-read from device on next [read]
     */
    suspend fun invalidate()

//    /**
//     * Update property logical value and notify listener without writing it to device
//     */
//    suspend fun update(item: MetaItem<*>)
//
    /**
     *  Get cached value and return null if value is invalid or not initialized
     */
    val value: MetaItem<*>?

    /**
     * Read value either from cache if cache is valid or directly from physical device.
     * If [force], reread
     */
    suspend fun read(force: Boolean = false): MetaItem<*>

    /**
     * The [Flow] representing future logical states of the property.
     * Produces null when the state is invalidated
     */
    fun flow(): Flow<MetaItem<*>?>

    override fun getValue(thisRef: Any?, property: KProperty<*>): MetaItem<*>? = value
}

/**
 * Launch recurring force re-read job on a property scope with given [duration] between reads.
 */
fun ReadOnlyDeviceProperty.readEvery(duration: Duration): Job = scope.launch {
    while (isActive) {
        read(true)
        delay(duration)
    }
}

/**
 * A writeable device property with non-suspended write
 */
interface DeviceProperty : ReadOnlyDeviceProperty, ReadWriteProperty<Any?, MetaItem<*>?> {
    override var value: MetaItem<*>?

    /**
     * Write value to physical device. Invalidates logical value, but does not update it automatically
     */
    suspend fun write(item: MetaItem<*>)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: MetaItem<*>?) {
        this.value = value
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): MetaItem<*>? = value
}

