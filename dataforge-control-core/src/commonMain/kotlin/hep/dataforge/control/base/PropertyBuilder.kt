package hep.dataforge.control.base

import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.transformations.MetaCaster
import hep.dataforge.values.Value

class PropertyBuilder<D : DeviceBase>(val name: String, val owner: D) {
    var descriptor: PropertyDescriptor = PropertyDescriptor.empty()

    inline fun descriptor(block: PropertyDescriptor.() -> Unit) {
        descriptor.apply(block)
    }

    fun <T : Any> get(converter: MetaCaster<T>, getter: (suspend D.() -> T)): GenericReadOnlyProperty<D, T> =
        GenericReadOnlyProperty(name, descriptor, owner, converter, getter)

    fun getDouble(getter: (suspend D.() -> Double)): GenericReadOnlyProperty<D, Double> =
        GenericReadOnlyProperty(name, descriptor, owner, MetaCaster.double) { getter() }

    fun getString(getter: suspend D.() -> String): GenericReadOnlyProperty<D, String> =
        GenericReadOnlyProperty(name, descriptor, owner, MetaCaster.string) { getter() }

    fun getBoolean(getter: suspend D.() -> Boolean): GenericReadOnlyProperty<D, Boolean> =
        GenericReadOnlyProperty(name, descriptor, owner, MetaCaster.boolean) { getter() }

    fun getValue(getter: suspend D.() -> Any?): GenericReadOnlyProperty<D, Value> =
        GenericReadOnlyProperty(name, descriptor, owner, MetaCaster.value) { Value.of(getter()) }

    /**
     * Convert this read-only property to read-write property
     */
    infix fun <T: Any> GenericReadOnlyProperty<D, T>.set(setter: (suspend D.(oldValue: T?, newValue: T) -> Unit)): GenericProperty<D,T> {
        return GenericProperty(name, descriptor, owner, converter, getter, setter)
    }

    /**
     * Create read-write property with synchronized setter which updates value after set
     */
    fun <T: Any> GenericReadOnlyProperty<D, T>.set(synchronousSetter: (suspend D.(oldValue: T?, newValue: T) -> T)): GenericProperty<D,T> {
        val setter: suspend D.(oldValue: T?, newValue: T) -> Unit = { oldValue, newValue ->
            val result = synchronousSetter(oldValue, newValue)
            updateValue(result)
        }
        return GenericProperty(name, descriptor, owner, converter, getter, setter)
    }
}