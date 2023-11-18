package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.PropertyDescriptor
import kotlin.reflect.KProperty

internal actual fun PropertyDescriptor.fromSpec(property: KProperty<*>){}

internal actual fun ActionDescriptor.fromSpec(property: KProperty<*>){}