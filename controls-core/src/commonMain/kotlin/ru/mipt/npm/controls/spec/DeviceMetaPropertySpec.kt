package ru.mipt.npm.controls.spec

import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.transformations.MetaConverter

internal object DeviceMetaPropertySpec: DevicePropertySpec<Device,Meta> {
    override val descriptor: PropertyDescriptor = PropertyDescriptor("@meta")

    override val converter: MetaConverter<Meta> = MetaConverter.meta

    @InternalDeviceAPI
    override suspend fun read(device: Device): Meta = device.meta
}