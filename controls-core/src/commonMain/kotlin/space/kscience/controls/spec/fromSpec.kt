package space.kscience.controls.spec

import space.kscience.controls.api.ActionDescriptorBuilder
import space.kscience.controls.api.PropertyDescriptorBuilder
import kotlin.reflect.KProperty


internal expect fun PropertyDescriptorBuilder.fromSpec(property: KProperty<*>)

internal expect fun ActionDescriptorBuilder.fromSpec(property: KProperty<*>)