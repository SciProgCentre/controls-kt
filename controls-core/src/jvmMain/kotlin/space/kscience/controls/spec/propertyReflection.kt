package space.kscience.controls.spec

import space.kscience.controls.api.PropertyDescriptor
import kotlin.reflect.KProperty

internal fun PropertyDescriptor.fromSpec(property: KProperty<*>){
    property.annotations
}