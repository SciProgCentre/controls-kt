package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.PropertyDescriptor
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

internal actual fun PropertyDescriptor.fromSpec(property: KProperty<*>) {
    property.findAnnotation<Description>()?.let {
        description = it.content
    }
}

internal actual fun ActionDescriptor.fromSpec(property: KProperty<*>){
    property.findAnnotation<Description>()?.let {
        description = it.content
    }
}