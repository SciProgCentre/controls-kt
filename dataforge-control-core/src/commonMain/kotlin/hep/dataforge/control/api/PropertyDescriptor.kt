package hep.dataforge.control.api

import hep.dataforge.meta.Scheme
import hep.dataforge.meta.SchemeSpec

/**
 * A descriptor for property
 */
class PropertyDescriptor : Scheme() {
    //var name by string { error("Property name is mandatory") }
    //var descriptor by spec(ItemDescriptor)

    companion object : SchemeSpec<PropertyDescriptor>(::PropertyDescriptor)
}