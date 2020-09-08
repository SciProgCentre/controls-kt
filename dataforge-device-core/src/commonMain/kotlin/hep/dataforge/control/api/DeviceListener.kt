package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem

/**
 * PropertyChangeListener Interface
 * [value] is a new value that property has after a change; null is for invalid state.
 */
public interface DeviceListener {
    public fun propertyChanged(propertyName: String, value: MetaItem<*>?)
    public fun actionExecuted(action: String, argument: MetaItem<*>?, result: MetaItem<*>?) {}

    //TODO add general message listener method
}