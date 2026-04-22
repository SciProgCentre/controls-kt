package space.kscience.controls.demo.proto

import space.kscience.controls.api.*
import space.kscience.controls.proto.ProtoDevice
import space.kscience.controls.spec.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.*

class DemoDevice(context: Context, meta: Meta) : ProtoDevice(context, meta) {
    companion object : DeviceSpec<DemoDevice>(), Factory<DemoDevice> {
        private var stabilizationProfileState = StabilizationProfile()

        val voltage by mutableDoubleProperty(
            read = { 0.0 },
            write = { _, _ -> }
        )

        val current by doubleProperty(
            read = { 0.0 }
        )

        val mainSwitch by mutableNumberProperty(
            descriptorBuilder = {
                metaDescriptor {
                    attributes {
                        "rust_type" put "int"
                    }
                }
            },
            read = { 0 },
            write = { _, _ -> }
        )

        val channel by mutableNumberProperty(
            descriptorBuilder = {
                metaDescriptor {
                    attributes {
                        "rust_type" put "int"
                    }
                }
            },
            read = { 0 },
            write = { _, _ -> }
        )

        val stabilizationProfile by mutableProperty(
            converter = StabilizationProfile,
            descriptorBuilder = {
                metaDescriptor {
                    attributes {
                        "rust_type" put "meta"
                    }
                }
            },
            read = { stabilizationProfileState },
            write = { _, value -> stabilizationProfileState = value }
        )

        override fun build(context: Context, meta: Meta): DemoDevice = DemoDevice(context, meta)
    }
}
