package hep.dataforge.control.api

import hep.dataforge.meta.scheme.Scheme
import hep.dataforge.meta.scheme.SchemeSpec

/**
 * A descriptor for property
 */
class RequestDescriptor : Scheme() {
    //var name by string { error("Property name is mandatory") }
    //var descriptor by spec(ItemDescriptor)

    companion object : SchemeSpec<RequestDescriptor>(::RequestDescriptor)
}