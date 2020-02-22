package hep.dataforge.control.base

import hep.dataforge.control.api.Device
import hep.dataforge.control.api.PropertyChangeListener
import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.control.api.RequestDescriptor
import hep.dataforge.meta.MetaItem
import kotlin.jvm.JvmStatic
import kotlin.reflect.KProperty

abstract class DeviceBase : Device, PropertyChangeListener {
    private val properties = HashMap<String, ReadOnlyProperty>()
    private val requests = HashMap<String, Request>()

    override var controller: PropertyChangeListener? = null

    override fun propertyChanged(propertyName: String, value: MetaItem<*>) {
        controller?.propertyChanged(propertyName, value)
    }

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val requestDescriptors: Collection<RequestDescriptor>
        get() = requests.values.map { it.descriptor }

    fun <P : ReadOnlyProperty> initProperty(prop: P): P {
        properties[prop.name] = prop
        return prop
    }

    fun initRequest(request: Request): Request {
        requests[request.name] = request
        return request
    }

    protected fun initRequest(
        name: String,
        descriptor: RequestDescriptor = RequestDescriptor.empty(),
        block: suspend (MetaItem<*>?) -> MetaItem<*>?
    ): Request {
        val request = SimpleRequest(name, descriptor, block)
        return initRequest(request)
    }

    override suspend fun getProperty(propertyName: String): MetaItem<*> =
        (properties[propertyName] ?: error("Property with name $propertyName not defined")).read()

    override suspend fun setProperty(propertyName: String, value: MetaItem<*>) {
        (properties[propertyName] as? Property ?: error("Property with name $propertyName not defined")).write(value)
    }

    override suspend fun request(name: String, argument: MetaItem<*>?): MetaItem<*>? =
        (requests[name] ?: error("Request with name $name not defined")).invoke(argument)

    companion object {
        @JvmStatic
        protected fun <D : DeviceBase, P : ReadOnlyProperty> D.initProperty(
            name: String,
            builder: PropertyBuilder<D>.() -> P
        ): P {
            val property = PropertyBuilder(name, this).run(builder)
            initProperty(property)
            return property
        }
    }
}

class PropertyDelegateProvider<D : DeviceBase, T : Any, P : GenericReadOnlyProperty<D, T>>(
    val owner: D,
    val builder: PropertyBuilder<D>.() -> P
) {
    operator fun provideDelegate(thisRef: D, property: KProperty<*>): P {
        val name = property.name
        return owner.initProperty(PropertyBuilder(name, owner).run(builder))
    }
}

fun <D : DeviceBase, T : Any> D.property(
    builder: PropertyBuilder<D>.() -> GenericReadOnlyProperty<D, T>
): PropertyDelegateProvider<D, T, GenericReadOnlyProperty<D, T>> {
    return PropertyDelegateProvider(this, builder)
}

fun <D : DeviceBase, T : Any> D.mutableProperty(
    builder: PropertyBuilder<D>.() -> GenericProperty<D, T>
): PropertyDelegateProvider<D, T, GenericProperty<D, T>> {
    return PropertyDelegateProvider(this, builder)
}


