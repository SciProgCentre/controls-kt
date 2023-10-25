package space.kscience.controls.spec

import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.names.NameToken
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.mapValues
import kotlin.collections.mutableMapOf
import kotlin.collections.set


public class DeviceTree(
    public val deviceManager: DeviceManager,
    public val meta: Meta,
    builder: Builder,
) : DeviceHub {
    public class Builder(public val manager: DeviceManager) {
        internal val childrenFactories = mutableMapOf<NameToken, Factory<Device>>()

        public fun <D : Device> device(name: String, factory: Factory<Device>) {
            childrenFactories[NameToken.parse(name)] = factory
        }
    }

    override val devices: Map<NameToken, Device> = builder.childrenFactories.mapValues { (token, factory) ->
        val devicesMeta = meta["devices"]
        factory.build(deviceManager.context, devicesMeta?.get(token) ?: Meta.EMPTY)
    }

}