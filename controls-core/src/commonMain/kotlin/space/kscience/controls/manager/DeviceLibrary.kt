package space.kscience.controls.manager

import space.kscience.controls.api.DeviceTreeFactory
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.meta.descriptors.required
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.parseAsName
import space.kscience.dataforge.provider.Provider

/**
 * A library of device factories that can be used to create devices.
 */
public interface DeviceLibrary : Provider {
    public val factories: Map<String, DeviceTreeFactory>

    override val defaultTarget: String get() = DeviceManager.DEVICE_FACTORY_TARGET

    override fun content(target: String): Map<Name, Any>  = when(target){
        DeviceManager.DEVICE_FACTORY_TARGET -> factories.mapKeys { it.key.parseAsName() }
        else -> super.content(target)
    }
}

/**
 * A specification for [DeviceLibrary] factory.
 */
public object DeviceLibraryMetaSpec: MetaSpec(){
    public val type: MetaRef<String> by string { required() }
    public val name: MetaRef<String> by string()

    public val parameters: MetaRef<Meta> by metaItem()
}
