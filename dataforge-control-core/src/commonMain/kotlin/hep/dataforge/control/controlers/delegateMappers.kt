package hep.dataforge.control.controlers

import hep.dataforge.control.base.DeviceProperty
import hep.dataforge.control.base.ReadOnlyDeviceProperty
import hep.dataforge.meta.double
import hep.dataforge.meta.map
import hep.dataforge.meta.transform

fun ReadOnlyDeviceProperty.double() = map { it.double }
fun DeviceProperty.double() = transform { it.double ?: Double.NaN }
