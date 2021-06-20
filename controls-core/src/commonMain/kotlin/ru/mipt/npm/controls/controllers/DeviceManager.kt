package ru.mipt.npm.controls.controllers

import ru.mipt.npm.controls.api.Device
import ru.mipt.npm.controls.api.DeviceHub
import space.kscience.dataforge.context.*
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaBuilder
import space.kscience.dataforge.meta.get
import space.kscience.dataforge.meta.string
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

public class DeviceManager(override val deviceName: String = "") : AbstractPlugin(), DeviceHub {
    override val tag: PluginTag get() = Companion.tag

    /**
     * Actual list of connected devices
     */
    private val top = HashMap<NameToken, Device>()
    override val devices: Map<NameToken, Device> get() = top

    public val controller: HubController by lazy {
        HubController(this)
    }

    public fun registerDevice(name: NameToken, device: Device) {
        top[name] = device
    }

    override fun content(target: String): Map<Name, Any> = super<DeviceHub>.content(target)

    public companion object : PluginFactory<DeviceManager> {
        override val tag: PluginTag = PluginTag("devices", group = PluginTag.DATAFORGE_GROUP)
        override val type: KClass<out DeviceManager> = DeviceManager::class

        override fun invoke(meta: Meta, context: Context): DeviceManager =
            DeviceManager(meta["deviceName"].string ?: "")
    }
}

public interface DeviceFactory<D : Device> : Factory<D>

public val Context.devices: DeviceManager get() = plugins.fetch(DeviceManager)

public fun <D : Device> DeviceManager.install(name: String, factory: DeviceFactory<D>, meta: Meta = Meta.EMPTY): D {
    val device = factory(meta, context)
    registerDevice(NameToken(name), device)
    return device
}

public fun <D : Device> DeviceManager.installing(
    factory: DeviceFactory<D>,
    metaBuilder: MetaBuilder.() -> Unit = {},
): ReadOnlyProperty<Any?, D> = ReadOnlyProperty { _, property ->
    val name = property.name
    install(name, factory, Meta(metaBuilder))
}

