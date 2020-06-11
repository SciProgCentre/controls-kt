package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem

/**
 * PropertyChangeListener Interface
 * [value] is a new value that property has after a change; null is for invalid state.
 */
interface PropertyChangeListener {
    fun propertyChanged(propertyName: String, value: MetaItem<*>?)
}