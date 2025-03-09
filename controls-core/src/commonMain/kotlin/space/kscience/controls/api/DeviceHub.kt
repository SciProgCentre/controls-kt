package space.kscience.controls.api

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.provider.Path
import space.kscience.dataforge.provider.Provider
import space.kscience.dataforge.provider.asPath
import space.kscience.dataforge.provider.plus

/**
 * A hub that could locate multiple devices and redirect actions to them
 */
public interface DeviceHub : Provider {
    public val devices: Map<Name, Device>

    override val defaultTarget: String get() = Device.DEVICE_TARGET

    override val defaultChainTarget: String get() = Device.DEVICE_TARGET

    override fun content(target: String): Map<Name, Any> = if (target == Device.DEVICE_TARGET) {
        devices
    } else {
        emptyMap()
    }
    //TODO send message on device change

    public companion object
}

public fun DeviceHub(deviceMap: Map<Name, Device>): DeviceHub = object : DeviceHub {
    override val devices: Map<Name, Device>
        get() = deviceMap
}

/**
 * List all devices, including sub-devices
 */
public fun DeviceHub.provideAllDevices(): Map<Path, Device> = buildMap {
    fun putAll(prefix: Path, hub: DeviceHub) {
        hub.devices.forEach {
            put(prefix + it.key.asPath(), it.value)
        }
    }

    devices.forEach {
        val name: Name = it.key
        put(name.asPath(), it.value)
        (it.value as? DeviceHub)?.let { hub ->
            putAll(name.asPath(), hub)
        }
    }
}

public suspend fun DeviceHub.readProperty(deviceName: Name, propertyName: String): Meta =
    (devices[deviceName] ?: error("Device with name $deviceName not found in $this")).readProperty(propertyName)

public suspend fun DeviceHub.writeProperty(deviceName: Name, propertyName: String, value: Meta) {
    (devices[deviceName] ?: error("Device with name $deviceName not found in $this")).writeProperty(propertyName, value)
}

public suspend fun DeviceHub.execute(deviceName: Name, command: String, argument: Meta?): Meta? =
    (devices[deviceName] ?: error("Device with name $deviceName not found in $this")).execute(command, argument)