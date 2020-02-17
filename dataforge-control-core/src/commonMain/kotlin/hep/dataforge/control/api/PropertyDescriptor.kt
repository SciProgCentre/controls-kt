package hep.dataforge.control.api

import hep.dataforge.meta.scheme.Scheme
import hep.dataforge.meta.scheme.SchemeSpec
import hep.dataforge.meta.scheme.string

/**
 * A descriptor for property
 */
class PropertyDescriptor: Scheme()  {
    var name by string{ error("Property name is mandatory")}
    //var descriptor by spec(ItemDescriptor)

    companion object: SchemeSpec<PropertyDescriptor>(::PropertyDescriptor)
}