package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.transformations.MetaCaster
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class GenericReadOnlyProperty<D: DeviceBase, T : Any>(
    override val name: String,
    override val descriptor: PropertyDescriptor,
    override val owner: D,
    internal val converter: MetaCaster<T>,
    internal val getter: suspend D.() -> T
) : ReadOnlyProperty, kotlin.properties.ReadOnlyProperty<Any?, T?> {

    protected val mutex = Mutex()
    protected var value: T? = null

    suspend fun updateValue(value: T) {
        mutex.withLock { this.value = value }
        owner.propertyChanged(name, converter.objectToMetaItem(value))
    }

    suspend fun readValue(): T =
        value ?: withContext(owner.scope.coroutineContext) {
            //all device operations should be run on device context
            owner.getter().also { updateValue(it) }
        }

    fun peekValue(): T? = value

    suspend fun update(item: MetaItem<*>) {
        updateValue(converter.itemToObject(item))
    }

    override suspend fun read(): MetaItem<*> = converter.objectToMetaItem(readValue())

    override fun peek(): MetaItem<*>? = value?.let { converter.objectToMetaItem(it) }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = peekValue()
}

class GenericProperty<D: DeviceBase, T : Any>(
    name: String,
    descriptor: PropertyDescriptor,
    owner: D,
    converter: MetaCaster<T>,
    getter: suspend D.() -> T,
    private val setter: suspend D.(oldValue: T?, newValue: T) -> Unit
) : Property, ReadWriteProperty<Any?, T?>, GenericReadOnlyProperty<D, T>(name, descriptor, owner, converter, getter) {

    suspend fun writeValue(newValue: T) {
        val oldValue = value
        withContext(owner.scope.coroutineContext) {
            //all device operations should be run on device context
            invalidate()
            owner.setter(oldValue, newValue)
        }
    }

    override suspend fun invalidate() {
        mutex.withLock { value = null }
    }

    override suspend fun write(item: MetaItem<*>) {
        writeValue(converter.itemToObject(item))
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        owner.scope.launch {
            if (value == null) {
                invalidate()
            } else {
                writeValue(value)
            }
        }
    }

}