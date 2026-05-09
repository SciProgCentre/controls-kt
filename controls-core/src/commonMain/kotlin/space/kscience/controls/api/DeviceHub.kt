package space.kscience.controls.api

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.isEmpty
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

/**
 * Create a device hub from a map of devices
 */
public fun DeviceHub(deviceMap: Map<Name, Device>): DeviceHub = object : DeviceHub {
    override val devices: Map<Name, Device> get() = deviceMap
}

/**
 * A device that is also a device hub
 */
public interface ParentDevice : Device, DeviceHub {
    override suspend fun start() {
        super.start()
        devices.values.forEach { it.start() }
    }

    override suspend fun stop() {
        devices.values.forEach { it.stop() }
        super.stop()
    }
}

/**
 * Resolve a device by its name. Throw an exception if the device is not found.
 */
public fun ParentDevice.resolveDevice(name: Name): Device =
    if (name.isEmpty()) this else devices[name] ?: error("Device $name not found in $this")

/**
 * Resolve a device by its name. Throw an exception if the device is not found.
 */
public fun DeviceHub.resolveDevice(name: Name): Device =
    if (name.isEmpty()) {
        (this as? Device ?: error("Device hub is not a device. It could not be accessed with empty name"))
    } else devices[name]
        ?: error("Device $name not found in $this")

/**
 * Create a device hub that is also a device itself by providing a device mapping and root device.
 *
 * Children devices are started automatically when the parent device is started and stopped before the parent is stopped.
 */
public fun ParentDevice(rootDevice: Device, children: Map<Name, Device>): ParentDevice =
    object : ParentDevice, Device by rootDevice {
        override val devices: Map<Name, Device> get() = children

        override suspend fun start() {
            rootDevice.start()
            devices.values.forEach { it.start() }
        }

        override suspend fun stop() {
            devices.values.forEach { it.stop() }
            rootDevice.stop()
        }
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
    resolveDevice(deviceName).readProperty(propertyName)

public suspend fun DeviceHub.writeProperty(deviceName: Name, propertyName: String, value: Meta) {
    resolveDevice(deviceName).writeProperty(propertyName, value)
}

public suspend fun DeviceHub.execute(deviceName: Name, command: String, argument: Meta?): Meta? =
    resolveDevice(deviceName).execute(command, argument)