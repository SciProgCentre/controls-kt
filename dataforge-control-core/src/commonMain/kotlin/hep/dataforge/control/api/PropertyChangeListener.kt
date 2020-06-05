package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem

interface PropertyChangeListener {
    fun propertyChanged(propertyName: String, value: MetaItem<*>?)
}