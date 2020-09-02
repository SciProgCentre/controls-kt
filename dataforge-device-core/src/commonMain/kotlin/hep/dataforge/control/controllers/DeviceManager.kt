package hep.dataforge.control.controllers

import hep.dataforge.context.AbstractPlugin
import hep.dataforge.context.Context
import hep.dataforge.context.PluginFactory
import hep.dataforge.context.PluginTag
import hep.dataforge.control.api.Device
import hep.dataforge.control.api.DeviceHub
import hep.dataforge.meta.Meta
import hep.dataforge.names.Name
import hep.dataforge.names.NameToken
import kotlin.reflect.KClass

class DeviceManager : AbstractPlugin(), DeviceHub {
    override val tag: PluginTag get() = Companion.tag

    /**
     * Actual list of connected devices
     */
    private val top = HashMap<NameToken, Device>()
    override val devices: Map<NameToken, Device> get() = top

    val controller by lazy {
        HubController(this, context)
    }

    fun registerDevice(name: NameToken, device: Device) {
        top[name] = device
    }

    override fun provideTop(target: String): Map<Name, Any> = super<DeviceHub>.provideTop(target)

    companion object : PluginFactory<DeviceManager> {
        override val tag: PluginTag = PluginTag("devices", group = PluginTag.DATAFORGE_GROUP)
        override val type: KClass<out DeviceManager> = DeviceManager::class

        override fun invoke(meta: Meta, context: Context): DeviceManager = DeviceManager()
    }
}


val Context.devices: DeviceManager get() = plugins.fetch(DeviceManager)

