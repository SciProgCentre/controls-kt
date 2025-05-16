package space.kscience.controls.spec

import space.kscience.controls.api.Device
import space.kscience.controls.api.PropertyDescriptor
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter

internal object DeviceMetaPropertySpec : DevicePropertySpec<Device, Meta> {
    override val descriptor: PropertyDescriptor = PropertyDescriptor("@meta")

    override val converter: MetaConverter<Meta> = MetaConverter.meta

    @InternalDeviceAPI
    override suspend fun read(device: Device): Meta = device.meta
}