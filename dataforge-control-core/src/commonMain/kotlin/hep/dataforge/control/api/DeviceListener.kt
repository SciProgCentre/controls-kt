package hep.dataforge.control.api

import hep.dataforge.meta.MetaItem

interface DeviceListener {
    fun propertyChanged(propertyName: String, value: MetaItem<*>?)
    //TODO add general message listener method
}