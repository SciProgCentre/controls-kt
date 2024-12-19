package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptorBuilder
import space.kscience.controls.api.PropertyDescriptorBuilder
import kotlin.reflect.KProperty

internal actual fun PropertyDescriptorBuilder.fromSpec(property: KProperty<*>) {}

internal actual fun ActionDescriptorBuilder.fromSpec(property: KProperty<*>){}