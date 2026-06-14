package space.kscience.controls.api

import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.descriptors.Described
import space.kscience.dataforge.meta.descriptors.MetaDescriptor

/**
 * A factory interface for creating instances of [DeviceTree].
 *
 */
public interface DeviceTreeFactory : Factory<DeviceTree>, Described

/**
 * A [DeviceTreeFactory] that produces a [DeviceTree] with a single root [Device]
 */
public interface DeviceFactory : DeviceTreeFactory {

    override val descriptor: MetaDescriptor? get() = null

    public fun buildDevice(context: Context, meta: Meta): Device

    override fun build(context: Context, meta: Meta): DeviceTree = DeviceTree(buildDevice(context, meta))
}

public fun DeviceFactory(
    descriptor: MetaDescriptor? = null,
    block: (context: Context, meta: Meta) -> Device
): DeviceFactory = object : DeviceFactory {
    override fun buildDevice(
        context: Context,
        meta: Meta
    ): Device = block(context, meta)


    override val descriptor: MetaDescriptor? = descriptor

}
