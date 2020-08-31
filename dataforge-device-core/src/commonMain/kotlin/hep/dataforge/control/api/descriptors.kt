package hep.dataforge.control.api

import hep.dataforge.meta.Scheme
import hep.dataforge.meta.string

/**
 * A descriptor for property
 */
class PropertyDescriptor(name: String) : Scheme() {
    val name by string(name)
    var info by string()
}

/**
 * A descriptor for property
 */
class ActionDescriptor(name: String) : Scheme() {
    val name by string(name)
    var info by string()
    //var descriptor by spec(ItemDescriptor)
}

