package space.kscience.controls.manager

import space.kscience.controls.api.Device
import space.kscience.dataforge.context.Factory
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.provider.Provider

/**
 * A library of device factories that can be used to create devices.
 */
public interface DeviceLibrary : Provider {
    public val factories: Map<Name, Factory<Device>>

    override val defaultTarget: String get() = DeviceManager.DEVICE_FACTORY_TARGET

    override fun content(target: String): Map<Name, Any>  = when(target){
        DeviceManager.DEVICE_FACTORY_TARGET -> factories
        else -> super.content(target)
    }
}