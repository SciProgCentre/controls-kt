package ru.mipt.npm.controls.properties

import space.kscience.dataforge.meta.transformations.MetaConverter
import kotlin.properties.ReadWriteProperty

public fun <D : DeviceBySpec<D>> D.state(
    initialValue: Double,
): ReadWriteProperty<D, Double> = state(MetaConverter.double, initialValue)

public fun <D : DeviceBySpec<D>> D.state(
    initialValue: Number,
): ReadWriteProperty<D, Number> = state(MetaConverter.number, initialValue)