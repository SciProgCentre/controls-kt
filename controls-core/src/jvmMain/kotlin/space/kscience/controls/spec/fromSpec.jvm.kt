package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptorBuilder
import space.kscience.controls.api.PropertyDescriptorBuilder
import space.kscience.dataforge.descriptors.Description
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

internal actual fun PropertyDescriptorBuilder.fromSpec(property: KProperty<*>) {
    property.findAnnotation<Description>()?.let {
        description = it.value
    }
}

internal actual fun ActionDescriptorBuilder.fromSpec(property: KProperty<*>){
    property.findAnnotation<Description>()?.let {
        description = it.value
    }
}