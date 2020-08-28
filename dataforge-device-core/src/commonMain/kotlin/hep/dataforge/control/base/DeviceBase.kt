package hep.dataforge.control.base

import hep.dataforge.control.api.ActionDescriptor
import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceListener
import hep.dataforge.control.api.PropertyDescriptor
import hep.dataforge.meta.MetaItem

/**
 * Baseline implementation of [Device] interface
 */
abstract class DeviceBase : Device {
    private val properties = HashMap<String, ReadOnlyDeviceProperty>()
    private val actions = HashMap<String, Action>()

    private val listeners = ArrayList<Pair<Any?, DeviceListener>>(4)

    override fun registerListener(listener: DeviceListener, owner: Any?) {
        listeners.add(owner to listener)
    }

    override fun removeListeners(owner: Any?) {
        listeners.removeAll { it.first == owner }
    }

    internal fun propertyChanged(propertyName: String, value: MetaItem<*>?) {
        listeners.forEach { it.second.propertyChanged(propertyName, value) }
    }

    override val propertyDescriptors: Collection<PropertyDescriptor>
        get() = properties.values.map { it.descriptor }

    override val actionDescriptors: Collection<ActionDescriptor>
        get() = actions.values.map { it.descriptor }

    internal fun registerProperty(name: String, builder: () -> ReadOnlyDeviceProperty): ReadOnlyDeviceProperty {
        return properties.getOrPut(name, builder)
    }

    internal fun registerAction(name: String, builder: () -> Action): Action {
        return actions.getOrPut(name, builder)
    }

    override suspend fun getProperty(propertyName: String): MetaItem<*> =
        (properties[propertyName] ?: error("Property with name $propertyName not defined")).read()

    override suspend fun invalidateProperty(propertyName: String) {
        (properties[propertyName] ?: error("Property with name $propertyName not defined")).invalidate()
    }

    override suspend fun setProperty(propertyName: String, value: MetaItem<*>) {
        (properties[propertyName] as? DeviceProperty ?: error("Property with name $propertyName not defined")).write(
            value
        )
    }

    override suspend fun execute(action: String, argument: MetaItem<*>?): MetaItem<*>? =
        (actions[action] ?: error("Request with name $action not defined")).invoke(argument)


    companion object {

    }
}



