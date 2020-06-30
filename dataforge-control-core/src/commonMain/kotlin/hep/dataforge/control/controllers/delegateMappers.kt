package hep.dataforge.control.controllers

import hep.dataforge.control.base.DeviceProperty
import hep.dataforge.control.base.ReadOnlyDeviceProperty
import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.double
import hep.dataforge.meta.map
import hep.dataforge.values.asValue

fun ReadOnlyDeviceProperty.double() = map { it.double }
fun DeviceProperty.double() = map(
    reader = { it.double ?: Double.NaN },
    writer = { MetaItem.ValueItem(it.asValue()) }
)
