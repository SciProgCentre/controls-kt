package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.descriptors.Description
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

internal actual fun PropertyDescriptor.fromSpec(property: KProperty<*>) {
    property.findAnnotation<Description>()?.let {
        description = it.value
    }
}

internal actual fun ActionDescriptor.fromSpec(property: KProperty<*>){
    property.findAnnotation<Description>()?.let {
        description = it.value
    }
}