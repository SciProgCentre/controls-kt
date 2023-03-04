package space.kscience.controls.manager

import kotlinx.coroutines.launch
import space.kscience.controls.api.Device
import space.kscience.controls.api.DeviceHub
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MutableMeta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import kotlin.collections.set
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

public class DeviceManager : AbstractPlugin(), DeviceHub {
    override val tag: PluginTag get() = Companion.tag

    /**
     * Actual list of connected devices
     */
    private val top = HashMap<NameToken, Device>()
    override val devices: Map<NameToken, Device> get() = top

    public fun registerDevice(name: NameToken, device: Device) {
        top[name] = device
    }

    override fun content(target: String): Map<Name, Any> = super<DeviceHub>.content(target)

    public companion object : PluginFactory<DeviceManager> {
        override val tag: PluginTag = PluginTag("devices", group = PluginTag.DATAFORGE_GROUP)
        override val type: KClass<out DeviceManager> = DeviceManager::class

        override fun build(context: Context, meta: Meta): DeviceManager = DeviceManager()
    }
}


public fun <D : Device> DeviceManager.install(name: String, factory: Factory<D>, meta: Meta = Meta.EMPTY): D {
    val device = factory(meta, context)
    registerDevice(NameToken(name), device)
    device.launch {
        device.open()
    }
    return device
}

public inline fun <D : Device> DeviceManager.installing(
    factory: Factory<D>,
    builder: MutableMeta.() -> Unit = {},
): ReadOnlyProperty<Any?, D> {
    val meta = Meta(builder)
    return ReadOnlyProperty { _, property ->
        val name = property.name
        install(name, factory, meta)
    }
}

