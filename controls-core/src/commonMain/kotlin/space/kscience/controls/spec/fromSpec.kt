package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptor
import space.kscience.controls.api.PropertyDescriptor
import kotlin.reflect.KProperty


internal expect fun PropertyDescriptor.fromSpec(property: KProperty<*>)

internal expect fun ActionDescriptor.fromSpec(property: KProperty<*>)